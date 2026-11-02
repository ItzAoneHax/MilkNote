package com.milknote.agent

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** ⑤ 抽屉全量状态(§8.1 精简落地) */
data class DraftAttachment(
    val id: String,
    val logicalName: String,
    val widthPx: Int,
    val heightPx: Int,
    val png: ByteArray,
    val jpeg: Boolean,
    val anchor: Rect4?,                 // 选区页坐标(批注贴回定位)
    val source: Attachment.Source,
)

data class UiMessage(
    val id: String,
    val role: AgentMessage.Role,
    val text: String,
    val reasoning: String = "",              // 助手思考过程(默认折叠展示)
    val attachments: List<Attachment> = emptyList(),
    val intent: Intents.Id? = null,
    val modelId: String? = null,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val phase: AgentMessage.Phase = AgentMessage.Phase.DONE,
    val streaming: Boolean = false,
    val insertedAnnotationId: String? = null,
    val anchor: Rect4? = null,          // 首条用户消息的选区(贴批注锚点)
)

data class AgentUiState(
    val drawerOpen: Boolean = false,
    val setupRequired: Boolean = false, // 无可用 Provider/视觉模型 → 引导卡(§3.4)
    val profiles: List<ProviderProfile> = emptyList(),
    val activeProfileId: String? = null,
    val activeModelId: String? = null,
    val messages: List<UiMessage> = emptyList(),
    val draftText: String = "",
    val draftAttachments: List<DraftAttachment> = emptyList(),
    val phase: AgentMessage.Phase = AgentMessage.Phase.DONE,
    val error: AgentError? = null,
    val boundPage: PageRef? = null,
    val toast: String? = null,          // 一次性提示(chips 截断等)
)

/**
 * Agent 门面状态机(§8.2):④框选确认 → 上下文组装 → Provider 流式 → ⑤渲染 → 落盘/贴回。
 * 全部 IO 在 [scope];流式过程不落盘,Done/Failed 时整会话重写(§7.2)。
 */
class AgentController(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val store: AgentStore,
    private val snipSource: SnipSource?,
    private val annotationSink: AnnotationSink?,
    private val keyFor: (String) -> String?,                  // alias → 明文 Key(装配层接 KeyVault)
    private val providerFactory: (ProviderProfile, () -> String?, (CompatPatch) -> Unit) -> AgentProvider =
        { p, k, onPatch -> ProviderFactory.create(p, k, onPatch) },   // 按 protocol 分发(OpenAI 兼容/Anthropic 原生)
) {

    private val _state = MutableStateFlow(AgentUiState())
    val state: StateFlow<AgentUiState> = _state

    private var session: AgentSession? = null
    private var sendJob: Job? = null
    private var lastProvider: AgentProvider? = null   // stop() 取消在途请求用

    /** ModelNotVision 连坐计数(§3.4:连续 2 次本地标 vision=false) */
    private val notVisionStrikes = HashMap<String, Int>()

    // Key 写入桥(装配层注入 KeyVault;测试可换内存表)
    private var keySinkPut: (String, String) -> Unit = { _, _ -> }
    private var keySinkRemove: (String) -> Unit = {}

    init {
        refreshUiFromBook(store.loadProfiles())
    }

    private fun update(transform: (AgentUiState) -> AgentUiState) {
        _state.value = transform(_state.value)
    }

    // ==================== Provider / 配置管理(§12) ====================

    private fun currentBook(): AgentStore.ProfileBook {
        val s = _state.value
        return AgentStore.ProfileBook(s.profiles, s.activeProfileId)
    }

    private fun saveBookAndRefresh(book: AgentStore.ProfileBook) {
        store.saveProfiles(book)
        refreshUiFromBook(book)
    }

    private fun refreshUiFromBook(book: AgentStore.ProfileBook) {
        val active = book.profiles.firstOrNull { it.id == book.activeProfileId }
            ?: book.profiles.firstOrNull()
        val model = active?.activeModel()
        update {
            it.copy(
                profiles = book.profiles,
                activeProfileId = active?.id,
                activeModelId = model?.id,
                setupRequired = model == null || !model.vision,
            )
        }
    }

    /** 新增 profile(预设或自定义);apiKey 空 = 暂不可用;自定义可指定 Anthropic 原生协议 */
    fun addProfile(
        presetId: String,
        title: String,
        baseUrl: String,
        apiKey: String,
        protocol: Protocol = Protocol.OPENAI_COMPATIBLE,
    ) {
        val preset = ProviderRegistry.byId(presetId)
        val alias = "pk_" + UUID.randomUUID().toString().substring(0, 8)
        if (apiKey.isNotEmpty()) keySinkPut(alias, apiKey)
        val profile = if (preset != null) {
            ProviderRegistry.newProfile(preset, alias)
                .let { p ->
                    if (title.isNotBlank()) p.copy(title = title) else p
                }
                .let { p -> if (baseUrl.isNotBlank()) p.copy(baseUrl = baseUrl) else p }
        } else {
            ProviderProfile(
                id = UUID.randomUUID().toString().substring(0, 8),
                presetId = null,
                title = title.ifBlank { "自定义" },
                baseUrl = baseUrl,
                apiKeyAlias = alias,
                protocol = protocol,
            )
        }
        if (profile.baseUrl.isBlank()) {
            update { it.copy(toast = "请填写 baseUrl") }
            return
        }
        val book = currentBook().let { b ->
            b.copy(profiles = b.profiles + profile, activeProfileId = b.activeProfileId ?: profile.id)
        }
        saveBookAndRefresh(book)
    }

    /** Key 变更(设置页输入) */
    fun setApiKey(profileId: String, apiKey: String) {
        val p = _state.value.profiles.firstOrNull { it.id == profileId } ?: return
        keySinkPut(p.apiKeyAlias, apiKey)
        update { it.copy(toast = "Key 已保存(仅本机)") }
    }

    fun removeProfile(profileId: String) {
        val book = currentBook()
        val p = book.profiles.firstOrNull { it.id == profileId } ?: return
        var next = book.copy(profiles = book.profiles - p)
        if (next.activeProfileId == profileId) {
            next = next.copy(activeProfileId = next.profiles.firstOrNull()?.id)
        }
        keySinkRemove(p.apiKeyAlias)
        saveBookAndRefresh(next)
    }

    /** 整本替换(baseUrl 等就地编辑保存;仅设置页使用) */
    fun replaceProfiles(book: AgentStore.ProfileBook) {
        saveBookAndRefresh(book)
    }

    fun setActiveProfile(profileId: String) {
        saveBookAndRefresh(currentBook().copy(activeProfileId = profileId))
    }

    /** 增加模型:预设/元数据/手填声明(§3.4 自定义=用户声明 vision) */
    fun addModel(profileId: String, modelId: String, declaredVision: Boolean, maxImages: Int = 8) {
        val modelId = modelId.trim()
        if (modelId.isEmpty() || !declaredVision) {
            update { it.copy(toast = if (modelId.isEmpty()) "modelId 不能为空" else "需勾选「已确认支持图片」") }
            return
        }
        val book = currentBook()
        val updated = book.profiles.map { p ->
            if (p.id != profileId) p
            else {
                val m = ModelInfo(modelId, modelId, vision = true, visionSource = VisionSource.DECLARED.name, maxImages = maxImages)
                if (p.models.any { it.id == modelId }) p
                else p.copy(models = p.models + m, activeModelId = p.activeModelId.ifEmpty { modelId })
            }
        }
        saveBookAndRefresh(book.copy(profiles = updated))
    }

    /** 模型行编辑(harness ModelRow 同款:显示名/视觉声明/思考档位/容量) */
    fun updateModel(
        profileId: String,
        modelId: String,
        displayName: String? = null,
        vision: Boolean? = null,
        thinkingLevels: List<ReasoningEffort>? = null,
        contextWindowK: Int? = null,
        maxTokensK: Int? = null,
    ) {
        val book = currentBook()
        val updated = book.profiles.map { p ->
            if (p.id != profileId) p
            else p.copy(models = p.models.map {
                if (it.id != modelId) it
                else it.copy(
                    displayName = displayName ?: it.displayName,
                    vision = vision ?: it.vision,
                    // 视觉随输入类型声明走:勾「图片」= vision=true(来源 DECLARED)
                    visionSource = if (vision == true) VisionSource.DECLARED.name else it.visionSource,
                    thinkingLevels = thinkingLevels?.map { l -> l.name } ?: it.thinkingLevels,
                    contextWindowK = contextWindowK ?: it.contextWindowK,
                    maxTokensK = maxTokensK ?: it.maxTokensK,
                )
            })
        }
        saveBookAndRefresh(book.copy(profiles = updated))
    }

    /** 整表替换(fetch 勾选器应用/重置;harness picker 同款语义) */
    fun setModels(profileId: String, models: List<ModelInfo>) {
        val book = currentBook()
        val updated = book.profiles.map { p ->
            if (p.id != profileId) p
            else p.copy(
                models = models,
                activeModelId = if (models.any { it.id == p.activeModelId }) p.activeModelId
                else models.firstOrNull { it.vision }?.id ?: "",
            )
        }
        saveBookAndRefresh(book.copy(profiles = updated))
    }

    fun removeModel(profileId: String, modelId: String) {
        val book = currentBook()
        val updated = book.profiles.map { p ->
            if (p.id != profileId) p
            else {
                val models = p.models.filter { it.id != modelId }
                p.copy(
                    models = models,
                    activeModelId = if (p.activeModelId == modelId) models.firstOrNull { it.vision }?.id ?: "" else p.activeModelId,
                )
            }
        }
        saveBookAndRefresh(book.copy(profiles = updated))
    }

    /** 思考强度(§3.8):落 active profile,AUTO=不发参数;下次发送生效 */
    fun setReasoningEffort(effort: ReasoningEffort) {
        val book = currentBook()
        val updated = book.copy(profiles = book.profiles.map {
            if (it.id == book.activeProfileId) it.copy(reasoningEffort = effort.name) else it
        })
        saveBookAndRefresh(updated)
        update { it.copy(toast = "思考强度:${effortLabel(effort)}") }
    }

    private fun effortLabel(e: ReasoningEffort): String = when (e) {
        ReasoningEffort.AUTO -> "自动(模型默认)"
        ReasoningEffort.OFF -> "关"
        ReasoningEffort.MINIMAL -> "最低"
        ReasoningEffort.LOW -> "低"
        ReasoningEffort.MEDIUM -> "中"
        ReasoningEffort.HIGH -> "高"
        ReasoningEffort.XHIGH -> "超高"
        ReasoningEffort.MAX -> "最高"
    }

    /** 切模型(仅视觉;下次发送生效,不重发历史) */
    fun selectModel(profileId: String, modelId: String) {
        val book = currentBook()
        val updated = book.profiles.map {
            if (it.id == profileId) it.copy(activeModelId = modelId) else it
        }
        saveBookAndRefresh(book.copy(profiles = updated))
        session = session?.copy(modelId = modelId)
    }

    /** 拉取模型列表(尽力而为;OpenRouter 元数据自动标 vision) */
    suspend fun fetchModels(profileId: String): Result<List<ModelInfo>> {
        val p = _state.value.profiles.firstOrNull { it.id == profileId }
            ?: return Result.failure(IllegalStateException("profile 不存在"))
        val provider = providerFactory(p, { keyFor(p.apiKeyAlias) }, {})
        return provider.fetchModels()
    }

    /** 测试连接(1-token 纯文本,仅验鉴权) */
    suspend fun testConnection(profileId: String): Result<Long> {
        val p = _state.value.profiles.firstOrNull { it.id == profileId }
            ?: return Result.failure(IllegalStateException("profile 不存在"))
        val provider = providerFactory(p, { keyFor(p.apiKeyAlias) }, {})
        return provider.testConnection()
    }

    // ==================== 抽屉 / 会话(§7.1) ====================

    /** 绑定页面(书写页 open/switch 时调用;不弹抽屉,供框选流程先行) */
    fun bindPage(page: PageRef) {
        val cur = session
        if (cur == null || cur.pageId != page.pageId || cur.notebookId != page.notebookId) {
            session = store.latestSession(page.notebookId, page.pageId) ?: AgentSession(
                id = UUID.randomUUID().toString().substring(0, 8),
                notebookId = page.notebookId,
                pageId = page.pageId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                providerId = _state.value.activeProfileId ?: "",
                modelId = _state.value.activeModelId ?: "",
            )
            update {
                AgentUiState(
                    profiles = it.profiles,
                    activeProfileId = it.activeProfileId,
                    activeModelId = it.activeModelId,
                    setupRequired = it.setupRequired,
                    boundPage = page,
                    messages = session?.messages?.map { m -> m.toUi() } ?: emptyList(),
                )
            }
        }
    }

    /** ⑤ 打开:恢复该页最近会话(无则建空) */
    fun openDrawer(page: PageRef) {
        bindPage(page)
        _state.value = _state.value.copy(
            drawerOpen = true,
            draftText = "",
            phase = AgentMessage.Phase.DONE,
            error = null,
        )
    }

    /** 收起:流不中断,后台收完落盘(§8.2 closeDrawer keepStream=true) */
    fun closeDrawer() {
        update { it.copy(drawerOpen = false) }
    }

    /** /清空:开新会话(旧会话文件保留可回看) */
    fun clearSession() {
        val p = _state.value.boundPage ?: return
        session = AgentSession(
            id = UUID.randomUUID().toString().substring(0, 8),
            notebookId = p.notebookId,
            pageId = p.pageId,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            providerId = _state.value.activeProfileId ?: "",
            modelId = _state.value.activeModelId ?: "",
        )
        update { it.copy(messages = emptyList(), draftAttachments = emptyList(), draftText = "") }
    }

    private fun AgentMessage.toUi() = UiMessage(
        id = id, role = role, text = text, reasoning = reasoning, attachments = attachments,
        intent = intent?.let { runCatching { Intents.Id.valueOf(it) }.getOrNull() },
        modelId = modelId, promptTokens = promptTokens, completionTokens = completionTokens,
        phase = AgentMessage.Phase.valueOf(phase),
        insertedAnnotationId = insertedAnnotationId,
    )

    // ==================== 框选(§4.2 → 附件) ====================

    /** ④ 选区确认:渲染→编码→草稿附件(不落盘);返回逻辑名(null=失败) */
    suspend fun addSnip(rect: Rect4): String? {
        val page = _state.value.boundPage ?: return null
        val src = snipSource ?: return null
        val res = src.captureSnip(page, rect) ?: return null
        val draft = DraftAttachment(
            id = UUID.randomUUID().toString().substring(0, 8),
            logicalName = res.logicalName,
            widthPx = res.widthPx,
            heightPx = res.heightPx,
            png = res.png,
            jpeg = res.jpeg,
            anchor = rect,
            source = Attachment.Source.SNIP,
        )
        update { it.copy(draftAttachments = it.draftAttachments + draft, drawerOpen = true) }
        return res.logicalName
    }

    // ==================== 发送(§2.2 链路) ====================

    fun setDraftText(text: String) = update { it.copy(draftText = text) }

    /** ④ 意图条「发送 ↵」:带意图首轮(或抽屉开着时多选区累积后发送) */
    fun sendWithIntent(intent: Intents.Id, extraText: String = "") {
        launchSend(intent, extraText)
    }

    // ---- 📎 附件面板(A2.6,ZCode 式:附件挂 composer、✕ 可移除、随下一条消息发出) ----

    /** 附加整页(当前页或指定页) */
    suspend fun addPageAttachment(page: PageRef): String? {
        val src = snipSource ?: return null
        val res = src.capturePage(page) ?: return null
        val draft = DraftAttachment(
            id = UUID.randomUUID().toString().substring(0, 8),
            logicalName = res.logicalName,
            widthPx = res.widthPx, heightPx = res.heightPx,
            png = res.png, jpeg = res.jpeg,
            anchor = null, source = Attachment.Source.FULL_PAGE,
        )
        update { it.copy(draftAttachments = it.draftAttachments + draft, drawerOpen = true) }
        return res.logicalName
    }

    /** 附加整章(其余页,按模型图预算截断);返回添加张数 */
    suspend fun addChapterAttachments(): Int {
        val bound = _state.value.boundPage ?: return 0
        val src = snipSource ?: return 0
        val pages = src.pagesOf(bound.notebookId)
        val cap = ImageBudget.chapterPageCount(pages.size, imagesUsed = 0, max = ImageBudget.maxImagesFor(activeModelInfo()))
        val rest = pages.filter { it.pageId != bound.pageId }.takeLast(cap)
        var added = 0
        for (p in rest) {
            val res = src.capturePage(p) ?: continue
            update { st ->
                st.copy(draftAttachments = st.draftAttachments + DraftAttachment(
                    id = UUID.randomUUID().toString().substring(0, 8),
                    logicalName = res.logicalName,
                    widthPx = res.widthPx, heightPx = res.heightPx,
                    png = res.png, jpeg = res.jpeg,
                    anchor = null, source = Attachment.Source.EXTRA_PAGE,
                ))
            }
            added++
        }
        if (added > 0) update { it.copy(drawerOpen = true) }
        return added
    }

    /** 移除草稿附件 */
    fun removeDraftAttachment(id: String) {
        update { it.copy(draftAttachments = it.draftAttachments.filterNot { d -> d.id == id }) }
    }

    /** ⑤ 输入框「发送」:追问(意图上下文保留;可无图) */
    fun send() {
        val s = _state.value
        if (s.draftText.isBlank() && s.draftAttachments.isEmpty()) return
        launchSend(null, s.draftText)
    }

    fun stop() {
        sendJob?.cancel()
        lastProvider?.cancel()
        val s = _state.value
        val idx = s.messages.indexOfLast { it.streaming }
        if (idx >= 0) {
            val msgs = s.messages.toMutableList()
            val m = msgs[idx]
            msgs[idx] = m.copy(phase = AgentMessage.Phase.INTERRUPTED, streaming = false)
            update { it.copy(messages = msgs, phase = AgentMessage.Phase.INTERRUPTED) }
            persistSession()
        }
    }

    /** 失败条幅「重试」:整轮重发替换半截(§8.2) */
    fun retry() {
        val s = _state.value
        // 找最后一个用户消息,丢掉其后的助手消息重发
        val lastUser = s.messages.indexOfLast { it.role == AgentMessage.Role.USER }
        if (lastUser < 0) return
        // 恢复其意图与附件为草稿
        val userMsg = s.messages[lastUser]
        val restored = userMsg.attachments.mapNotNull { a ->
            // 已落盘附件重新读回草稿(引用传递)
            val bytes = store.readAttachment(session!!.notebookId, a) ?: return@mapNotNull null
            DraftAttachment(a.id, a.logicalName, a.widthPx, a.heightPx, bytes, jpeg = false, anchor = userMsg.anchor, source = a.source)
        }
        _state.value = s.copy(
            messages = s.messages.subList(0, lastUser),
            draftAttachments = s.draftAttachments + restored,
        )
        session = session?.copy(messages = session!!.messages.subList(0, lastUser))
        launchSend(userMsg.intent, "")
    }

    fun consumeToast() = update { it.copy(toast = null) }

    // ---- 发送管线 ----

    private fun launchSend(intent: Intents.Id?, extraText: String) {
        val s = _state.value
        val page = s.boundPage
        val profile = s.profiles.firstOrNull { it.id == s.activeProfileId }
        val model = profile?.activeModel()
        if (profile == null || model == null || !model.vision || keyFor(profile.apiKeyAlias).isNullOrEmpty()) {
            update { it.copy(setupRequired = true, drawerOpen = true) }
            return
        }
        if (page == null || session == null) {
            update { it.copy(toast = "请先打开一个页面") }
            return
        }
        sendJob?.cancel()

        val spec = intent?.let { Intents.byId(it) }
        val intentForChips = intent
        scope.launch {
            val phase0 = _state.value
            // 1. 上下文图 = 草稿附件(框选 + 📎 显式附加;A2.6 起不再隐式带当前页/整章)
            val images = ArrayList(phase0.draftAttachments)
            val capped = ImageBudget.truncate(images, ImageBudget.maxImagesFor(model))
            if (capped.second > 0) {
                update { it.copy(toast = "已达单请求图上限,已截去最早 ${capped.second} 张") }
            }
            val finalImages = capped.first

            // 2. 附件落盘(发送时才写,§4.3)
            val atts = finalImages.map { d ->
                store.storeAttachment(
                    page.notebookId, d.png, d.jpeg, d.widthPx, d.heightPx, d.source, d.logicalName,
                )
            }

            // 3. 消息入状态
            val userMsg = UiMessage(
                id = UUID.randomUUID().toString().substring(0, 8),
                role = AgentMessage.Role.USER,
                text = extraText.ifBlank { spec?.label?.let { "[$it]" } ?: "" },
                attachments = atts,
                intent = intent,
                anchor = finalImages.firstOrNull { it.anchor != null }?.anchor,
            )
            val asstMsg = UiMessage(
                id = UUID.randomUUID().toString().substring(0, 8),
                role = AgentMessage.Role.ASSISTANT,
                text = "",
                modelId = model.id,
                phase = AgentMessage.Phase.STREAMING,
                streaming = true,
            )
            update {
                it.copy(
                    messages = it.messages + userMsg + asstMsg,
                    draftText = "",
                    draftAttachments = emptyList(),
                    phase = AgentMessage.Phase.STREAMING,
                    error = null,
                )
            }

            // 4. 请求(历史只带文字,图在最后一条 user;§6.2)
            val history = historyWindow()
            val userWire = if (spec != null) PromptEngine.userText(spec, extraText) else extraText
            val req = ChatRequest(
                model = model.id,
                system = PromptEngine.systemPrompt(Intents.byId(intent ?: lastIntent() ?: Intents.Id.EXPLAIN)),
                history = history,
                newUserText = PromptEngine.assembleUserContent(userWire, finalImages.size),
                images = finalImages.map { java.util.Base64.getEncoder().encodeToString(it.png) },
                imageMediaTypes = finalImages.map { if (it.jpeg) "image/jpeg" else "image/png" },
                imageStyle = profile.imageStyle,
                reasoningEffort = runCatching { ReasoningEffort.valueOf(profile.reasoningEffort) }
                    .getOrDefault(ReasoningEffort.AUTO),
            )
            runExchange(profile, model, req, userMsg, asstMsg, intentForChips)
        }
    }

    private fun lastIntent(): Intents.Id? =
        _state.value.messages.lastOrNull { it.role == AgentMessage.Role.USER && it.intent != null }?.intent

    private fun historyWindow(): List<WireMessage> {
        val msgs = _state.value.messages
            .filter { !it.streaming && it.phase != AgentMessage.Phase.FAILED && it.text.isNotBlank() }
            .map { (if (it.role == AgentMessage.Role.USER) "user" else "assistant") to it.text }
        return PromptEngine.windowedHistory(msgs)
    }

    /** 流式交换 + §11 重试策略(自动重试仅首 token 前) */
    private fun runExchange(
        profile: ProviderProfile,
        model: ModelInfo,
        req: ChatRequest,
        userMsg: UiMessage,
        asstMsg: UiMessage,
        intent: Intents.Id?,
    ) {
        sendJob = scope.launch {
            var attempt = 0
            val text = StringBuilder()
            val thinking = StringBuilder()
            var usage: ChatEvent.Usage? = null

            fun replaceAssistant(transform: (UiMessage) -> UiMessage) {
                update { s ->
                    s.copy(messages = s.messages.map { if (it.id == asstMsg.id) transform(it) else it })
                }
            }

            while (true) {
                val provider = providerFactory(profile, { keyFor(profile.apiKeyAlias) }) { patch ->
                    // 兼容性自适配回写(§3.5:imageStyle / streamOptions),落盘后续不再抖动
                    store.saveProfiles(currentBook().let { b ->
                        b.copy(profiles = b.profiles.map {
                            if (it.id == profile.id) it.copy(
                                imageStyle = patch.imageStyle ?: it.imageStyle,
                                streamOptionsEnabled = patch.streamOptions ?: it.streamOptionsEnabled,
                            ) else it
                        })
                    })
                }
                lastProvider = provider

                var done: ChatEvent.Done? = null
                var err: AgentError? = null
                var gotDelta = false
                try {
                    provider.chat(req).collect { ev ->
                        when (ev) {
                            is ChatEvent.Finish -> {}   // 完成信号仅记录;Done 在 [DONE] 或流关闭时发
                            is ChatEvent.Thinking -> {
                                gotDelta = true   // 思考也是已计费内容:不进自动重试(§11)
                                thinking.append(ev.text)
                                replaceAssistant { it.copy(reasoning = thinking.toString(), streaming = true) }
                            }
                            is ChatEvent.Delta -> {
                                gotDelta = true
                                text.append(ev.text)
                                replaceAssistant { it.copy(text = text.toString(), streaming = true) }
                            }
                            is ChatEvent.Usage -> usage = ev
                            is ChatEvent.Done -> done = ev
                            is ChatEvent.Error -> err = ev.error
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                }

                if (done != null) {
                    replaceAssistant {
                        it.copy(
                            streaming = false,
                            phase = AgentMessage.Phase.DONE,
                            promptTokens = usage?.promptTokens
                                ?: ImageBudget.estTokens(req.images.size, req.newUserText.length),
                            completionTokens = usage?.completionTokens
                                ?: (text.length / 1.6).toInt(),
                        )
                    }
                    update { it.copy(phase = AgentMessage.Phase.DONE) }
                    persistWith(userMsg, asstMsg, text.toString(), AgentMessage.Phase.DONE, intent, usage, model.id, thinking.toString())
                    return@launch
                }

                val e = err
                if (e == null) {   // 流自然结束无 [DONE]:按完成处理(防御)
                    replaceAssistant { it.copy(streaming = false, phase = AgentMessage.Phase.DONE) }
                    persistWith(userMsg, asstMsg, text.toString(), AgentMessage.Phase.DONE, intent, usage, model.id, thinking.toString())
                    return@launch
                }

                // 取消
                if (e is AgentError.Cancelled) {
                    replaceAssistant { it.copy(streaming = false, phase = AgentMessage.Phase.INTERRUPTED) }
                    update { it.copy(phase = AgentMessage.Phase.INTERRUPTED) }
                    persistWith(userMsg, asstMsg, text.toString(), AgentMessage.Phase.INTERRUPTED, intent, usage, model.id, thinking.toString())
                    return@launch
                }

                // 已有内容 → 不自动重试(不重复计费,§11)
                if (gotDelta || text.isNotEmpty()) {
                    replaceAssistant { it.copy(streaming = false, phase = AgentMessage.Phase.FAILED) }
                    update { it.copy(phase = AgentMessage.Phase.FAILED, error = AgentError.StreamBroken) }
                    persistWith(userMsg, asstMsg, text.toString(), AgentMessage.Phase.FAILED, intent, usage, model.id, thinking.toString())
                    return@launch
                }

                // ModelNotVision 连坐(§3.4):2 次后本地标 vision=false
                if (e is AgentError.ModelNotVision) {
                    val key = "${profile.id}/${model.id}"
                    val n = (notVisionStrikes[key] ?: 0) + 1
                    notVisionStrikes[key] = n
                    if (n >= 2) {
                        val marked = currentBook().let { b ->
                            b.copy(profiles = b.profiles.map { p ->
                                if (p.id == profile.id) p.copy(
                                    models = p.models.map { if (it.id == model.id) it.copy(vision = false) else it },
                                ) else p
                            })
                        }
                        store.saveProfiles(marked)
                        refreshUiFromBook(marked)
                    }
                }

                // 首 token 前自动重试(§11):RateLimit 2s/8s/30s ×3(服务端 Retry-After 优先);
                // Server 1s/5s ×2;EmptyResponse 1s ×1(空响应是可重试错误,不落空气泡)
                val backoff = when (e) {
                    is AgentError.RateLimit ->
                        if (attempt < 3) e.retryAfterMs ?: listOf(2_000L, 8_000L, 30_000L)[attempt] else null
                    is AgentError.Server -> listOf(1_000L, 5_000L).getOrNull(attempt)
                    is AgentError.EmptyResponse -> if (attempt == 0) 1_000L else null
                    else -> null
                }
                if (backoff != null) {
                    attempt++
                    delay(backoff)
                    continue
                }

                replaceAssistant { it.copy(streaming = false, phase = AgentMessage.Phase.FAILED) }
                update { it.copy(phase = AgentMessage.Phase.FAILED, error = e) }
                persistWith(userMsg, asstMsg, "", AgentMessage.Phase.FAILED, intent, null, model.id, thinking.toString())
                return@launch
            }
        }
    }

    private fun persistWith(
        userMsg: UiMessage,
        asstMsg: UiMessage,
        asstText: String,
        phase: AgentMessage.Phase,
        intent: Intents.Id?,
        usage: ChatEvent.Usage?,
        modelId: String,
        thinkingText: String = "",
    ) {
        val s = session ?: return
        val u = AgentMessage(
            id = userMsg.id, role = AgentMessage.Role.USER, sentAt = System.currentTimeMillis(),
            text = userMsg.text, attachments = userMsg.attachments, intent = intent?.name,
        )
        val a = AgentMessage(
            id = asstMsg.id, role = AgentMessage.Role.ASSISTANT, sentAt = System.currentTimeMillis(),
            text = asstText, reasoning = thinkingText, modelId = modelId,
            promptTokens = usage?.promptTokens ?: 0,
            completionTokens = usage?.completionTokens ?: 0,
            phase = phase.name,
        )
        val messages = (s.messages.filter { it.id != userMsg.id && it.id != asstMsg.id } + u + a)
            .sortedBy { it.sentAt }
        session = s.copy(
            messages = messages,
            updatedAt = System.currentTimeMillis(),
            providerId = _state.value.activeProfileId ?: s.providerId,
            modelId = modelId,
        )
        store.saveSession(session!!)
        // 会话内消息状态同步(重试/清空后的内存视图)
        val uiById = _state.value.messages.associateBy { it.id }
        session = session?.copy(
            messages = session!!.messages.map { m ->
                val ui = uiById[m.id]
                if (ui != null) m.copy(insertedAnnotationId = ui.insertedAnnotationId) else m
            },
        )
    }

    private fun persistSession() {
        val s = session ?: return
        val messages = s.messages + _state.value.messages
            .filter { ui -> s.messages.none { it.id == ui.id } }
            .map { ui ->
                AgentMessage(
                    id = ui.id, role = ui.role, sentAt = 0, text = ui.text,
                    attachments = ui.attachments, intent = ui.intent?.name, modelId = ui.modelId,
                    promptTokens = ui.promptTokens, completionTokens = ui.completionTokens,
                    phase = ui.phase.name,
                )
            }
        session = s.copy(messages = messages, updatedAt = System.currentTimeMillis())
        store.saveSession(session!!)
    }

    // ==================== 结果动作(§9) ====================

    /** 插入批注层:幂等(成功后消息记 insertedAnnotationId) */
    fun insertAnnotation(msgId: String) {
        val s = _state.value
        val msg = s.messages.firstOrNull { it.id == msgId } ?: return
        if (msg.insertedAnnotationId != null) {
            update { it.copy(toast = "已插入过批注") }
            return
        }
        val sink = annotationSink ?: return
        val page = s.boundPage ?: return
        val userAnchor = s.messages.lastOrNull { it.role == AgentMessage.Role.USER && it.anchor != null }?.anchor
        val idx = s.messages.indexOfFirst { it.id == msgId }
        val exchangeIntent = if (idx > 0) s.messages[idx - 1].intent else msg.intent
        val latex = exchangeIntent == Intents.Id.LATEX
        scope.launch {
            val id = sink.insertCard(page, msg.anchor ?: userAnchor, msg.text, latex, cardWidth = 0f)
            if (id == null) {
                update { it.copy(toast = "插入失败:页面不可达") }
            } else {
                update { st ->
                    st.copy(
                        messages = st.messages.map { if (it.id == msgId) it.copy(insertedAnnotationId = id) else it },
                        toast = "已插入批注层(可拖动定位)",
                    )
                }
                // 幂等标记落盘
                session = session?.copy(
                    messages = session!!.messages.map { if (it.id == msgId) it.copy(insertedAnnotationId = id) else it },
                )
                session?.let { store.saveSession(it) }
            }
        }
    }

    // ==================== Key 桥(装配层注入 KeyVault;测试可换内存表) ====================

    fun attachKeyVault(put: (String, String) -> Unit, remove: (String) -> Unit) {
        keySinkPut = put
        keySinkRemove = remove
    }

    fun hasKey(profile: ProviderProfile): Boolean = !keyFor(profile.apiKeyAlias).isNullOrEmpty()

    /** UI:当前请求参数摘要(模型胶囊 meta 行) */
    fun activeModelInfo(): ModelInfo? {
        val s = _state.value
        return s.profiles.firstOrNull { it.id == s.activeProfileId }?.activeModel()
    }

    /** ⌘K「讲解本页/归纳本章」:直接 ⑤,显式附加对应页后自动发送(§8.3) */
    fun quickIntent(intent: Intents.Id) {
        val page = _state.value.boundPage ?: return
        openDrawer(page)
        scope.launch {
            if (intent == Intents.Id.SUMMARIZE) addChapterAttachments() else addPageAttachment(page)
            sendWithIntent(intent)
        }
    }

    /** 上下文规模预估(附件区「≈ N tok」;仅按草稿附件计) */
    fun estTokensNow(): Int {
        val s = _state.value
        return ImageBudget.estTokens(s.draftAttachments.size, s.draftText.length)
    }
}
