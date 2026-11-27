package com.milknote.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

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
    val generationMs: Long = 0L,        // 首 token → 完成耗时(ms;0=未测量)
    val toolTraces: List<ToolTrace> = emptyList(),
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
    val currentSessionId: String? = null,
    val sessions: List<SessionListItem> = emptyList(),   // 当前笔记本全部会话(updatedAt DESC)
    val summaryConfig: SummaryConfig = SummaryConfig(),
    val autoMentionAttachments: Boolean = true,
    val summarizingSessionIds: Set<String> = emptySet(), // 正在生成摘要的会话
    val skills: List<AgentSkill> = emptyList(),
    val toolSpecs: List<ToolSpec> = emptyList(),
    val toolConfigs: Map<String, ToolConfig> = emptyMap(),
    val pendingConfirmation: PendingToolConfirmation? = null,
)

/** 摘要提示词(短、稳定;只要会话主题,不要复述细节) */
internal const val SUMMARY_SYSTEM_PROMPT =
    "你是笔记会话标题助手。只根据对话内容输出一句不超过 30 字的中文纯文本标题,概括主题与结论。" +
        "禁止继续回答对话中的问题,禁止引号、句号结尾、Markdown 格式、列表符号和换行。只输出这句标题。"

/** 单次请求附带的单个文档正文上限(防请求爆炸) */
private const val MAX_DOC_CHARS = 24000

/** 摘要输入摘要文本上限 */
private const val SUMMARY_TRANSCRIPT_MAX = 3000

/** 摘要输出上限(字符) */
private const val SUMMARY_MAX_CHARS = 80

/** 发送时拍平的单个视觉图(单图草稿,或多页文档中的某一页) */
private data class VisualDraft(
    val draft: DraftAttachment,
    val page: DraftPageImage?,
    val label: String,
) {
    val bytes: ByteArray get() = page?.png ?: draft.png
    val jpeg: Boolean get() = page?.jpeg ?: draft.jpeg
}

/**
 * Agent 门面状态机(§8.2):④框选确认 → 上下文组装 → Provider 流式 → ⑤渲染 → 落盘/贴回。
 * 全部 IO 在 [scope];流式过程不落盘,Done/Failed 时整会话重写(§7.2)。
 * [clockMs] 可注入,便于单测确定性地计算 generationMs / avgTokensPerSec。
 */
class AgentController(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val store: AgentStore,
    private val snipSource: SnipSource?,
    private val annotationSink: AnnotationSink?,
    private val keyFor: (String) -> String?,                  // alias → 明文 Key(装配层接 KeyVault)
    private val providerFactory: (ProviderProfile, () -> String?, (CompatPatch) -> Unit) -> AgentProvider =
        { p, k, onPatch -> ProviderFactory.create(p, k, onPatch) },   // 按 protocol 分发(OpenAI 兼容/Anthropic 原生)
    private val clockMs: () -> Long = System::currentTimeMillis,
    /** 笔记本工作区(页面/画布工具);null = 相关工具返回「未接入」 */
    private val workspace: NotebookWorkspacePort? = null,
    private val webSearch: WebSearchPort = DefaultWebSearchPort(),
    private val toolRegistry: ToolRegistry = BuiltinTools.createDefaultRegistry(),
) {

    private val _state = MutableStateFlow(AgentUiState())
    val state: StateFlow<AgentUiState> = _state

    private var session: AgentSession? = null
    /** 绑定代际:每次真实的页/会话导航 +1;授权分析只在发起代际有效,导航后永不复活 */
    @Volatile private var bindingEpoch = 0L
    /** 关键区锁:导航换绑(代际 + session)与授权落盘(复验 + 取快照 + 写盘)互斥 */
    private val bindingLock = Any()
    private var sendJob: Job? = null
    private var lastProvider: AgentProvider? = null   // stop() 取消在途请求用
    private val summaryJobs = HashMap<String, Job>()  // sessionId → 在途摘要任务
    private var generationStartedAt = 0L              // 本轮请求开始时刻(手停也可算耗时)
    private var generationFirstTokenAt = 0L

    /** 待确认工具调用:等待 UI 批准/拒绝 */
    @Volatile private var confirmDeferred: CompletableDeferred<Boolean>? = null

    /** 技能端口:读状态内存,写回 store(工具 create_skill 直接调用) */
    private val skillManager: SkillManagerPort = object : SkillManagerPort {
        override fun listSkills(): List<AgentSkill> = _state.value.skills

        override fun findEnabledSkill(idOrName: String): AgentSkill? = _state.value.skills.firstOrNull {
            it.enabled && (it.id.equals(idOrName, ignoreCase = true) || it.name == idOrName)
        }

        override fun createOrUpdateSkill(
            id: String?,
            name: String,
            summary: String,
            content: String,
            enabled: Boolean,
        ): AgentSkill {
            BuiltinSkills.all.firstOrNull { id != null && it.id.equals(id, ignoreCase = true) }
                ?.let { return it }
            val existing = _state.value.skills.firstOrNull {
                id != null && it.id.equals(id, ignoreCase = true)
            }
            val resolvedId = existing?.id ?: id?.takeIf { it.isNotBlank() }
                ?: uniqueSkillId(slugifySkillId(name))
            val skill = AgentSkill(
                id = resolvedId,
                name = name.trim().ifBlank { "未命名技能" },
                summary = summary.trim(),
                content = content,
                enabled = enabled,
                builtin = false,
                updatedAt = clockMs(),
            )
            persistSkills(_state.value.skills.filterNot { it.id == skill.id } + skill)
            return skill
        }
    }

    /** ModelNotVision 连坐计数(§3.4:连续 2 次本地标 vision=false) */
    private val notVisionStrikes = HashMap<String, Int>()

    // Key 写入桥(装配层注入 KeyVault;测试可换内存表)
    private var keySinkPut: (String, String) -> Unit = { _, _ -> }
    private var keySinkRemove: (String) -> Unit = {}

    init {
        refreshUiFromBook(store.loadProfiles())
        refreshSkillAndToolState()
    }

    private fun update(transform: (AgentUiState) -> AgentUiState) {
        _state.value = transform(_state.value)
    }

    // ==================== Provider / 配置管理(§12) ====================

    private fun currentBook(): AgentStore.ProfileBook {
        val s = _state.value
        return AgentStore.ProfileBook(
            profiles = s.profiles,
            activeProfileId = s.activeProfileId,
            summaryConfig = s.summaryConfig,
            autoMentionAttachments = s.autoMentionAttachments,
        )
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
                summaryConfig = book.summaryConfig,
                autoMentionAttachments = book.autoMentionAttachments,
            )
        }
    }

    // ==================== 技能 / 工具管理 ====================

    private fun refreshSkillAndToolState() {
        val skills = store.loadSkills()
        val configs = store.loadToolConfigs()
        update {
            it.copy(
                skills = skills,
                toolSpecs = toolRegistry.allSpecs(),
                toolConfigs = configs,
            )
        }
    }

    private fun persistSkills(skills: List<AgentSkill>) {
        store.saveSkills(skills)
        update { it.copy(skills = skills) }
    }

    /** 新增或更新自定义技能(内置技能只读:任何覆盖都被忽略;id 为空时按名称生成安全 slug) */
    fun upsertSkill(skill: AgentSkill) {
        val id = skill.id.ifBlank { uniqueSkillId(slugifySkillId(skill.name)) }
        if (BuiltinSkills.all.any { it.id == id }) {
            update { it.copy(toast = "内置技能只读,不能修改或覆盖") }
            return
        }
        val next = skill.copy(
            id = id,
            name = skill.name.trim().ifBlank { "未命名技能" },
            builtin = false,
            updatedAt = clockMs(),
        )
        persistSkills(_state.value.skills.filterNot { it.id == next.id } + next)
    }

    fun toggleSkill(id: String, enabled: Boolean) {
        val skills = _state.value.skills
        val target = skills.firstOrNull { it.id == id } ?: return
        if (target.builtin || BuiltinSkills.all.any { it.id == id }) {
            update { it.copy(toast = "内置技能只读,不可关闭") }
            return
        }
        persistSkills(
            skills.map { if (it.id == id) it.copy(enabled = enabled, updatedAt = clockMs()) else it },
        )
    }

    /** 删除技能:内置技能只读,不可删除 */
    fun deleteSkill(id: String) {
        val skill = _state.value.skills.firstOrNull { it.id == id } ?: return
        if (skill.builtin || BuiltinSkills.all.any { it.id == id }) {
            update { it.copy(toast = "内置技能只读,不可删除") }
            return
        }
        persistSkills(_state.value.skills.filterNot { it.id == id })
    }

    /** 内置技能定义恒为规范版本;保留入口仅做幂等校正(用户不可编辑内容) */
    fun resetBuiltinSkill(id: String) {
        val base = BuiltinSkills.all.firstOrNull { it.id == id } ?: return
        persistSkills(_state.value.skills.filterNot { it.id == id } + base)
    }

    fun updateToolConfig(config: ToolConfig) {
        if (toolRegistry.get(config.id)?.spec?.builtin == true || config.id in BuiltinTools.builtinToolIds) {
            update { it.copy(toast = "内置工具只读,不能修改设置") }
            return
        }
        val next = _state.value.toolConfigs + (config.id to config)
        store.saveToolConfigs(next)
        update { it.copy(toolConfigs = next) }
    }

    fun toggleTool(id: String, enabled: Boolean) {
        val spec = toolRegistry.get(id)?.spec ?: return
        if (spec.builtin) {
            update { it.copy(toast = "内置工具只读,不可关闭") }
            return
        }
        val existing = _state.value.toolConfigs[id] ?: ToolConfig(id = id)
        updateToolConfig(existing.copy(id = id, enabled = enabled))
    }

    /** 移除用户覆盖,回到工具声明默认值 */
    fun resetToolConfig(id: String) {
        val next = _state.value.toolConfigs - id
        store.saveToolConfigs(next)
        update { it.copy(toolConfigs = next) }
    }

    // ---- 高危工具确认 ----

    fun approvePendingTool() {
        if (_state.value.pendingConfirmation == null) return
        update { it.copy(pendingConfirmation = null) }
        confirmDeferred?.complete(true)
    }

    fun rejectPendingTool() {
        if (_state.value.pendingConfirmation == null) return
        update { it.copy(pendingConfirmation = null) }
        confirmDeferred?.complete(false)
    }

    private fun effectiveConfigOf(spec: ToolSpec): ToolConfig =
        toolRegistry.effectiveConfig(spec, _state.value.toolConfigs)

    private fun slugifySkillId(name: String): String {
        val ascii = name.lowercase().map { c ->
            if (c in 'a'..'z' || c in '0'..'9' || c == '_') c else '_'
        }.joinToString("").trim('_')
        return when {
            ascii.isBlank() -> "skill"
            ascii.first().isDigit() -> "skill_$ascii"
            else -> ascii
        }
    }

    private fun uniqueSkillId(base: String): String {
        val used = _state.value.skills.map { it.id }.toSet()
        if (base !in used) return base
        var n = 2
        while ("${base}_$n" in used) n++
        return "${base}_$n"
    }

    /** 新增 profile(预设或自定义);apiKey 空 = 暂不可用;自定义可指定 Anthropic 原生协议 */
    fun addProfile(
        presetId: String,
        title: String,
        baseUrl: String,
        apiKey: String,
        protocol: Protocol? = null,
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
                // 显式传协议才覆盖预设默认(Anthropic 预设不能被 OPENAI_COMPATIBLE 默认值冲掉)
                .let { p -> if (protocol != null) p.copy(protocol = protocol) else p }
        } else {
            ProviderProfile(
                id = UUID.randomUUID().toString().substring(0, 8),
                presetId = null,
                title = title.ifBlank { "自定义" },
                baseUrl = baseUrl,
                apiKeyAlias = alias,
                protocol = protocol ?: Protocol.OPENAI_COMPATIBLE,
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

    /**
     * 只改某个 profile 的 baseUrl:内部走 [currentBook],
     * 调用方无需整本回传 `ProfileBook`,也就不会误清摘要/自动提及配置。
     */
    fun updateBaseUrl(profileId: String, baseUrl: String) {
        val book = currentBook()
        if (book.profiles.none { it.id == profileId }) return
        val updated = book.profiles.map {
            if (it.id == profileId) it.copy(baseUrl = baseUrl.trim()) else it
        }
        saveBookAndRefresh(book.copy(profiles = updated))
    }

    fun setActiveProfile(profileId: String) {
        saveBookAndRefresh(currentBook().copy(activeProfileId = profileId))
    }

    /** 改某家网关的默认流式协议(模型级覆盖优先于它;OpenCode Go 这类一家多协议靠模型级覆盖) */
    fun updateProtocol(profileId: String, protocol: Protocol) {
        val book = currentBook()
        if (book.profiles.none { it.id == profileId }) return
        val updated = book.profiles.map {
            if (it.id == profileId) it.copy(protocol = protocol) else it
        }
        saveBookAndRefresh(book.copy(profiles = updated))
    }

    /** 增加模型:预设/元数据/手填声明(§3.4 自定义=用户声明 vision;可指定该模型的协议) */
    fun addModel(
        profileId: String,
        modelId: String,
        declaredVision: Boolean,
        maxImages: Int = 8,
        protocol: Protocol? = null,
    ) {
        val modelId = modelId.trim()
        if (modelId.isEmpty() || !declaredVision) {
            update { it.copy(toast = if (modelId.isEmpty()) "modelId 不能为空" else "需勾选「已确认支持图片」") }
            return
        }
        val book = currentBook()
        val updated = book.profiles.map { p ->
            if (p.id != profileId) p
            else {
                val m = ModelInfo(
                    modelId, modelId, vision = true,
                    visionSource = VisionSource.DECLARED.name,
                    maxImages = maxImages,
                    protocol = protocol,
                )
                if (p.models.any { it.id == modelId }) p
                else p.copy(models = p.models + m, activeModelId = p.activeModelId.ifEmpty { modelId })
            }
        }
        saveBookAndRefresh(book.copy(profiles = updated))
    }

    /** 模型行编辑(harness ModelRow 同款:显示名/视觉声明/思考档位/容量/协议覆盖) */
    fun updateModel(
        profileId: String,
        modelId: String,
        displayName: String? = null,
        vision: Boolean? = null,
        thinkingLevels: List<ReasoningEffort>? = null,
        contextWindowK: Int? = null,
        maxTokensK: Int? = null,
        protocol: Protocol? = null,
        clearProtocolOverride: Boolean = false,
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
                    // 协议覆盖:显式清除 → 回到跟随 profile 默认
                    protocol = when {
                        clearProtocolOverride -> null
                        protocol != null -> protocol
                        else -> it.protocol
                    },
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

    /**
     * 切模型(仅视觉;下次发送生效,不重发历史)。
     * 跨 Provider 选模型时 [activateProfile] 同步切换 activeProfileId 与会话归属 ——
     * 否则「选了 A 家的模型却仍按 B 家发请求」,端点与 Key 全对不上。
     */
    fun selectModel(profileId: String, modelId: String, activateProfile: Boolean = true) {
        val book = currentBook()
        if (book.profiles.none { it.id == profileId }) return
        val updated = book.profiles.map {
            if (it.id == profileId) it.copy(activeModelId = modelId) else it
        }
        val nextActive = if (activateProfile) profileId else book.activeProfileId
        saveBookAndRefresh(book.copy(profiles = updated, activeProfileId = nextActive))
        val cur = session
        session = cur?.copy(
            providerId = if (activateProfile) profileId else cur.providerId,
            modelId = modelId,
        )
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
        // 与聊天同源:默认模型的协议覆盖也要生效,否则测试连接打错端点
        val modelId = p.activeModelId.ifEmpty { p.models.firstOrNull()?.id ?: "" }
        val effective = p.copy(protocol = p.effectiveProtocol(modelId))
        val provider = providerFactory(effective, { keyFor(effective.apiKeyAlias) }, {})
        return provider.testConnection()
    }

    // ==================== 抽屉 / 会话(§7.1) ====================

    private fun newSession(page: PageRef): AgentSession = AgentSession(
        id = UUID.randomUUID().toString().substring(0, 8),
        notebookId = page.notebookId,
        pageId = page.pageId,
        createdAt = clockMs(),
        updatedAt = clockMs(),
        providerId = _state.value.activeProfileId ?: "",
        modelId = _state.value.activeModelId ?: "",
    )

    /** 绑定页面(书写页 open/switch 时调用;不弹抽屉,供框选流程先行) */
    fun bindPage(page: PageRef) {
        val cur = session
        if (cur != null && cur.pageId == page.pageId && cur.notebookId == page.notebookId) {
            // 同页重复绑定(展开抽屉必然命中)不必再同步扫盘 + JSON 解析全部会话文件:
            // 新会话/切会话/删除/发送各自都会 refreshSessions(),这里只补「列表还空着」的情况
            val before = _state.value
            val needsRefresh = before.sessions.isEmpty() || before.boundPage?.notebookId != page.notebookId
            update { it.copy(boundPage = page, currentSessionId = cur.id) }
            if (needsRefresh) refreshSessions()
            return
        }
        val target = store.latestSession(page.notebookId, page.pageId) ?: newSession(page)
        rebindSession(target)
        update { st ->
            st.copy(
                boundPage = page,
                messages = target.messages.map { m -> m.toUi() },
                currentSessionId = target.id,
                draftAttachments = emptyList(),
                draftText = "",
                error = null,
                phase = AgentMessage.Phase.DONE,
            )
        }
        refreshSessions()
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

    /** /清空:开新会话(旧会话落盘保留可回看;离开旧会话时补一次摘要) */
    fun clearSession() {
        val p = _state.value.boundPage ?: session?.let { PageRef(it.notebookId, it.pageId) } ?: return
        val fresh = newSession(p)
        var departing: AgentSession? = null
        synchronized(bindingLock) {
            // 旧会话落盘与换绑同临界区:避免在途授权分析的写入被旧快照覆盖或反向
            departing = session?.takeIf { it.messages.isNotEmpty() }
            departing?.let { store.saveSession(it) }
            bindingEpoch++
            session = fresh
        }
        val left = departing
        if (left != null) maybeScheduleAutoSummary(left, leaving = true)
        update {
            it.copy(
                messages = emptyList(),
                draftAttachments = emptyList(),
                draftText = "",
                error = null,
                phase = AgentMessage.Phase.DONE,
                currentSessionId = fresh.id,
            )
        }
        refreshSessions()
    }

    /** 重新加载当前笔记本全部会话(updatedAt 倒序;含尚未落盘的空当前会话) */
    fun refreshSessions() {
        val cur = session
        val notebookId = _state.value.boundPage?.notebookId ?: cur?.notebookId ?: return
        val saved = store.loadNotebookSessions(notebookId)
        val all = if (cur != null && saved.none { it.id == cur.id }) {
            (saved + cur).sortedByDescending { it.updatedAt }
        } else {
            saved
        }
        val generating = _state.value.summarizingSessionIds
        val items = all.map { s ->
            s.toListItem(isCurrent = s.id == cur?.id, summaryGenerating = s.id in generating)
        }
        update { it.copy(sessions = items, currentSessionId = cur?.id ?: it.currentSessionId) }
    }

    /** 切换会话(流式进行中拒绝,避免丢失半截回复;离开旧会话时补一次摘要) */
    fun switchSession(sessionId: String) {
        val s = _state.value
        if (s.messages.any { it.streaming } || s.phase == AgentMessage.Phase.STREAMING) {
            update { it.copy(toast = "回复生成中,请先停止") }
            return
        }
        val notebookId = s.boundPage?.notebookId ?: session?.notebookId ?: return
        if (sessionId == session?.id) {
            refreshSessions()
            return
        }
        val target = store.loadSession(notebookId, sessionId) ?: return
        val departing = session
        if (departing != null && departing.id != target.id) {
            maybeScheduleAutoSummary(departing, leaving = true)
        }
        rebindSession(target)
        // 仍停留在同一笔记本时保留当前页:切到旧会话后「当前页」/框选仍作用于正在看的页面
        val bound = _state.value.boundPage
        val nextPage = if (bound != null && bound.notebookId == target.notebookId) {
            bound
        } else {
            PageRef(target.notebookId, target.pageId)
        }
        update {
            it.copy(
                boundPage = nextPage,
                messages = target.messages.map { m -> m.toUi() },
                currentSessionId = target.id,
                draftAttachments = emptyList(),
                draftText = "",
                error = null,
                phase = AgentMessage.Phase.DONE,
            )
        }
        refreshSessions()
    }

    /** 删除会话(级联清理无人引用的附件;删当前会话则切到最近一条或新建空会话) */
    fun deleteSession(sessionId: String) {
        val notebookId = _state.value.boundPage?.notebookId ?: session?.notebookId ?: return
        val target = store.loadSession(notebookId, sessionId)
            ?: session?.takeIf { it.id == sessionId }
            ?: return
        cancelSummary(sessionId)
        store.deleteSession(target)
        if (session?.id == sessionId) {
            val remaining = store.loadNotebookSessions(notebookId)
            val next = remaining.firstOrNull { it.pageId == target.pageId }
                ?: remaining.firstOrNull()
                ?: newSession(PageRef(target.notebookId, target.pageId))
            rebindSession(next)
            update {
                it.copy(
                    boundPage = PageRef(next.notebookId, next.pageId),
                    messages = next.messages.map { m -> m.toUi() },
                    currentSessionId = next.id,
                    draftAttachments = emptyList(),
                    draftText = "",
                    error = null,
                    phase = AgentMessage.Phase.DONE,
                )
            }
        }
        refreshSessions()
    }

    /** 摘要配置落盘(默认开启;关闭后不再自动触发,手动刷新仍可用) */
    fun setSummaryConfig(config: SummaryConfig) {
        saveBookAndRefresh(currentBook().copy(summaryConfig = config))
        if (config.enabled) session?.let { maybeScheduleAutoSummary(it) }
    }

    /** 附件自动 @ 提及开关落盘 */
    fun setAutoMentionAttachments(enabled: Boolean) {
        saveBookAndRefresh(currentBook().copy(autoMentionAttachments = enabled))
    }

    /** 手动重新生成摘要(即使 summaryConfig.enabled=false 也执行) */
    fun regenerateSummary(sessionId: String) {
        val notebookId = _state.value.boundPage?.notebookId ?: session?.notebookId ?: return
        val target = when {
            session?.id == sessionId -> session
            else -> store.loadSession(notebookId, sessionId)
        } ?: return
        if (summaryJobs[sessionId]?.isActive == true) return
        scheduleSummary(target, force = true)
    }

    private fun AgentMessage.toUi() = UiMessage(
        id = id, role = role, text = text, reasoning = reasoning, attachments = attachments,
        intent = intent?.let { runCatching { Intents.Id.valueOf(it) }.getOrNull() },
        modelId = modelId, promptTokens = promptTokens, completionTokens = completionTokens,
        phase = AgentMessage.Phase.valueOf(phase),
        insertedAnnotationId = insertedAnnotationId,
        generationMs = generationMs,
        toolTraces = toolTraces,
    )

    // ==================== 框选(§4.2 → 附件) ====================

    /** ④ 选区确认:渲染→编码→草稿附件(不落盘);返回逻辑名(null=失败) */
    suspend fun addSnip(rect: Rect4): String? {
        val page = _state.value.boundPage ?: return null
        val src = snipSource ?: return null
        val res = src.captureSnip(page, rect) ?: return null
        appendDraft(
            newDraft(
                logicalName = res.logicalName,
                widthPx = res.widthPx,
                heightPx = res.heightPx,
                png = res.png,
                jpeg = res.jpeg,
                anchor = rect,
                source = Attachment.Source.SNIP,
                baseMention = "选区",
            ),
        )
        return res.logicalName
    }

    // ==================== 发送(§2.2 链路) ====================

    fun setDraftText(text: String) = update { it.copy(draftText = text) }

    /** ④ 意图条「发送 ↵」:带意图首轮(或抽屉开着时多选区累积后发送);授权类意图必须走显式申请入口 */
    fun sendWithIntent(intent: Intents.Id, extraText: String = "") {
        if (intent == Intents.Id.PROFICIENCY) {
            update { it.copy(toast = "请点击「申请熟练度分析」并确认后再发送") }
            return
        }
        launchSend(intent, extraText)
    }

    /**
     * 用户主动「申请熟练度分析」的唯一入口(UI 说明弹层确认后调用)。
     * 授权绑定「发起时的页 + 会话 + 绑定代际」:不落盘、不继承到普通对话/重试/切页/新会话/摘要;
     * 后台分析期间任何导航都会永久废止本次授权(不再读时序、不再执行工具、不写任何会话)。
     * 模型、工具与附件文本都无法触发本方法。
     */
    fun requestProficiencyAnalysis(extraText: String = "") {
        val page = _state.value.boundPage
        val current = session
        if (page == null || current == null) {
            update { it.copy(toast = "请先打开一个页面") }
            return
        }
        if (sendJob?.isActive == true) {
            update { it.copy(toast = "回复生成中,请先停止") }
            return
        }
        launchSend(
            Intents.Id.PROFICIENCY,
            extraText,
            grant = ProficiencyGrant(page, current.id, bindingEpoch),
        )
    }

    // ---- 📎 附件面板(A2.6,ZCode 式:附件挂 composer、✕ 可移除、随下一条消息发出) ----

    /** 附加整页(当前页或指定页) */
    suspend fun addPageAttachment(page: PageRef): String? {
        val src = snipSource ?: return null
        val res = src.capturePage(page) ?: return null
        appendDraft(
            newDraft(
                logicalName = res.logicalName,
                widthPx = res.widthPx, heightPx = res.heightPx,
                png = res.png, jpeg = res.jpeg,
                anchor = null, source = Attachment.Source.FULL_PAGE,
                baseMention = pageMention(page),
            ),
        )
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
            appendDraft(
                newDraft(
                    logicalName = res.logicalName,
                    widthPx = res.widthPx, heightPx = res.heightPx,
                    png = res.png, jpeg = res.jpeg,
                    anchor = null, source = Attachment.Source.EXTRA_PAGE,
                    baseMention = pageMention(p, pages),
                ),
            )
            added++
        }
        return added
    }

    /** 系统图片(相册/粘贴,非页面渲染);返回逻辑名(null=空数据) */
    fun addSystemImageAttachment(
        png: ByteArray,
        jpeg: Boolean = false,
        widthPx: Int = 0,
        heightPx: Int = 0,
        logicalName: String = if (jpeg) "image.jpg" else "image.png",
        mentionName: String = "",
    ): String? {
        if (png.isEmpty()) return null
        appendDraft(
            newDraft(
                logicalName = logicalName,
                widthPx = widthPx, heightPx = heightPx,
                png = png, jpeg = jpeg,
                anchor = null, source = Attachment.Source.SYSTEM_IMAGE,
                baseMention = mentionName.ifBlank { "图片" },
            ),
        )
        return logicalName
    }

    /**
     * 文档附件(Markdown/纯文本/PDF/PPT):
     * - 正文进 [DraftAttachment.textContent](发送时作为 [附件文档] 块)
     * - PDF/PPT 逐页图进 [DraftAttachment.extraPages](按逐页图发送)
     * 返回逻辑名(无任何内容时 null)。
     */
    fun addDocumentAttachment(
        logicalName: String,
        source: Attachment.Source,
        mimeType: String = defaultDocMime(source),
        textContent: String = "",
        png: ByteArray = ByteArray(0),
        jpeg: Boolean = false,
        widthPx: Int = 0,
        heightPx: Int = 0,
        extraPages: List<DraftPageImage> = emptyList(),
        pageRangeLabel: String = "",
        mentionName: String = "",
    ): String? {
        if (textContent.isBlank() && png.isEmpty() && extraPages.isEmpty()) return null
        appendDraft(
            newDraft(
                logicalName = logicalName,
                widthPx = widthPx, heightPx = heightPx,
                png = png, jpeg = jpeg,
                anchor = null, source = source,
                baseMention = mentionName.ifBlank { logicalName.substringBeforeLast('.') },
                mimeType = mimeType,
                pageRangeLabel = pageRangeLabel,
                textContent = textContent,
                extraPages = extraPages,
            ),
        )
        return logicalName
    }

    /** 笔记页范围附件(逐页渲染;多页时按页序发送并附页码标签) */
    suspend fun addPageRangeAttachment(pages: List<PageRef>, label: String = ""): String? {
        val src = snipSource ?: return null
        if (pages.isEmpty()) return null
        val order = src.pagesOf(pages.first().notebookId)
        val captured = ArrayList<DraftPageImage>()
        for (p in pages) {
            val res = src.capturePage(p) ?: continue
            val idx = order.indexOfFirst { it.pageId == p.pageId }
            captured.add(
                DraftPageImage(
                    pageNumber = if (idx >= 0) idx + 1 else captured.size + 1,
                    png = res.png,
                    jpeg = res.jpeg,
                    widthPx = res.widthPx,
                    heightPx = res.heightPx,
                ),
            )
        }
        if (captured.isEmpty()) return null
        val numbers = captured.map { it.pageNumber }
        val rangeLabel = label.ifBlank {
            if (numbers.min() == numbers.max()) "第${numbers.min()}页" else "第${numbers.min()}-${numbers.max()}页"
        }
        val cover = captured.first()
        val logicalName = "pages_${numbers.min()}-${numbers.max()}.${if (cover.jpeg) "jpg" else "png"}"
        appendDraft(
            newDraft(
                logicalName = logicalName,
                widthPx = cover.widthPx, heightPx = cover.heightPx,
                png = cover.png, jpeg = cover.jpeg,
                anchor = null, source = Attachment.Source.EXTRA_PAGE,
                baseMention = label.ifBlank { rangeLabel },
                mimeType = if (cover.jpeg) "image/jpeg" else "image/png",
                pageRangeLabel = rangeLabel,
                extraPages = captured,
            ),
        )
        return logicalName
    }

    /** 移除草稿附件(同时清掉草稿文字里对应的 `@名`) */
    fun removeDraftAttachment(id: String) {
        update { st ->
            val target = st.draftAttachments.firstOrNull { d -> d.id == id } ?: return@update st
            val text = if (target.mentionName.isBlank()) {
                st.draftText
            } else {
                removeMentionToken(st.draftText, target.mentionName)
            }
            st.copy(
                draftAttachments = st.draftAttachments.filterNot { d -> d.id == id },
                draftText = text,
            )
        }
    }

    /**
     * 就地替换草稿图片(裁剪/翻转/调色后):保留 id、位置(anchor)、@ 提及名与来源。
     * 返回 false = 草稿不存在或新图为空(状态不变)。
     */
    fun updateDraftAttachment(
        id: String,
        png: ByteArray,
        jpeg: Boolean,
        widthPx: Int,
        heightPx: Int,
    ): Boolean {
        if (png.isEmpty()) return false
        val exists = _state.value.draftAttachments.any { it.id == id }
        if (!exists) return false
        update { st ->
            st.copy(
                draftAttachments = st.draftAttachments.map { d ->
                    if (d.id != id) {
                        d
                    } else {
                        d.copy(
                            png = png,
                            jpeg = jpeg,
                            widthPx = widthPx,
                            heightPx = heightPx,
                            // 图片型草稿跟随编码切换 MIME;文档草稿保持原 MIME
                            mimeType = if (d.mimeType.startsWith("image/")) {
                                if (jpeg) "image/jpeg" else "image/png"
                            } else {
                                d.mimeType
                            },
                        )
                    }
                },
            )
        }
        return true
    }

    /** 手动 @ 挑附件(或自动追加):把 `@名 ` 追加到草稿文字(已有则跳过) */
    fun appendMentionToDraft(mentionName: String) {        val name = sanitizeMention(mentionName)
        if (name.isEmpty()) return
        update { st ->
            if (hasMentionToken(st.draftText, name)) {
                st
            } else {
                val text = st.draftText
                val sep = if (text.isEmpty() || text.endsWith(" ") || text.endsWith("\n")) "" else " "
                st.copy(draftText = text + sep + "@$name ")
            }
        }
    }

    /** 草稿附件加入后按开关自动补 `@名` */
    private fun autoMention(mentionName: String) {
        if (!_state.value.autoMentionAttachments) return
        appendMentionToDraft(mentionName)
    }

    private fun newDraft(
        logicalName: String,
        widthPx: Int,
        heightPx: Int,
        png: ByteArray,
        jpeg: Boolean,
        anchor: Rect4?,
        source: Attachment.Source,
        baseMention: String,
        mimeType: String = if (jpeg) "image/jpeg" else "image/png",
        pageRangeLabel: String = "",
        textContent: String = "",
        extraPages: List<DraftPageImage> = emptyList(),
    ): DraftAttachment = DraftAttachment(
        id = UUID.randomUUID().toString().substring(0, 8),
        logicalName = logicalName,
        widthPx = widthPx,
        heightPx = heightPx,
        png = png,
        jpeg = jpeg,
        anchor = anchor,
        source = source,
        mimeType = mimeType,
        mentionName = uniqueMention(baseMention),
        pageRangeLabel = pageRangeLabel,
        textContent = textContent,
        extraPages = extraPages,
    )

    private fun appendDraft(draft: DraftAttachment) {
        update { it.copy(draftAttachments = it.draftAttachments + draft, drawerOpen = true) }
        autoMention(draft.mentionName)
    }

    /** @ 名清洗:去掉 @ 与空白(空格/换行/全角空格) */
    private fun sanitizeMention(raw: String): String =
        raw.replace("@", "").replace(Regex("[\\s\\u3000]+"), "").trim()

    /** 同名去重:选区 / 选区2 / 选区3 … */
    private fun uniqueMention(base: String): String {
        val existing = _state.value.draftAttachments
        val cleaned = sanitizeMention(base).ifBlank { "附件" }
        if (existing.none { it.mentionName == cleaned }) return cleaned
        var n = 2
        while (existing.any { it.mentionName == "$cleaned$n" }) n++
        return "$cleaned$n"
    }

    /** `@名` 只匹配完整 token(后面不能紧跟字母/数字/下划线,避免「@选区」误伤「@选区2」) */
    private fun mentionTokenRegex(name: String): Regex =
        Regex(Regex.escape("@$name") + "(?![\\p{L}\\p{N}_])")

    private fun hasMentionToken(text: String, name: String): Boolean =
        mentionTokenRegex(name).containsMatchIn(text)

    private fun removeMentionToken(text: String, name: String): String {
        if (!hasMentionToken(text, name)) return text
        return mentionTokenRegex(name).replace(text, "")
            .replace(Regex("[ \\t]{2,}"), " ")
            .trim()
    }

    private fun pageMention(page: PageRef, pages: List<PageRef> = emptyList()): String {
        val idx = pages.indexOfFirst { it.pageId == page.pageId }
        if (idx >= 0) return "第${idx + 1}页"
        val number = Regex("^p0*(\\d+)$", RegexOption.IGNORE_CASE).find(page.pageId)?.groupValues?.getOrNull(1)
        return if (number != null) "第${number}页" else "第${page.pageId}页"
    }

    private fun defaultDocMime(source: Attachment.Source): String = when (source) {
        Attachment.Source.MARKDOWN_DOC -> "text/markdown"
        Attachment.Source.TEXT_DOC -> "text/plain"
        Attachment.Source.PDF_DOC -> "application/pdf"
        Attachment.Source.PPT_DOC -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        else -> "image/png"
    }

    /** ⑤ 输入框「发送」:追问(意图上下文保留;可无图) */
    fun send() {
        val s = _state.value
        if (s.draftText.isBlank() && s.draftAttachments.isEmpty()) return
        launchSend(null, s.draftText)
    }

    fun stop() {
        // 正在等待高危工具确认:先拒绝并清空,再取消任务(否则确认弹层会残留)
        if (_state.value.pendingConfirmation != null) {
            update { it.copy(pendingConfirmation = null) }
            confirmDeferred?.complete(false)
        }
        sendJob?.cancel()
        lastProvider?.cancel()
        val s = _state.value
        val idx = s.messages.indexOfLast { it.streaming }
        if (idx >= 0) {
            val msgs = s.messages.toMutableList()
            val m = msgs[idx]
            val genMs = measuredGenerationMs(generationStartedAt, generationFirstTokenAt, m.completionTokens)
            msgs[idx] = m.copy(
                phase = AgentMessage.Phase.INTERRUPTED,
                streaming = false,
                generationMs = if (m.generationMs > 0L) m.generationMs else genMs,
            )
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
        // 恢复其意图与附件为草稿(引用传递;多页文档连逐页图一起恢复)
        val userMsg = s.messages[lastUser]
        val notebookId = session?.notebookId ?: return
        val restored = userMsg.attachments.mapNotNull { a ->
            val pages = a.pageFiles.mapIndexedNotNull { i, rel ->
                val bytes = store.readRelativeFile(notebookId, rel) ?: return@mapIndexedNotNull null
                DraftPageImage(
                    pageNumber = i + 1,
                    png = bytes,
                    jpeg = rel.endsWith(".jpg", ignoreCase = true),
                    widthPx = a.widthPx,
                    heightPx = a.heightPx,
                )
            }
            val imageBytes = if (a.isImage) store.readAttachment(notebookId, a) ?: ByteArray(0) else ByteArray(0)
            if (imageBytes.isEmpty() && pages.isEmpty() && a.textContent.isBlank()) return@mapNotNull null
            DraftAttachment(
                id = a.id,
                logicalName = a.logicalName,
                widthPx = a.widthPx,
                heightPx = a.heightPx,
                png = imageBytes,
                jpeg = a.mimeType == "image/jpeg",
                anchor = userMsg.anchor,
                source = a.source,
                mimeType = a.mimeType,
                mentionName = a.mentionName,
                pageRangeLabel = a.pageRangeLabel,
                textContent = a.textContent,
                extraPages = pages,
            )
        }
        _state.value = s.copy(
            messages = s.messages.subList(0, lastUser),
            draftAttachments = s.draftAttachments + restored,
        )
        session = session?.copy(messages = session!!.messages.subList(0, lastUser))
        // 重试不继承熟练度分析授权;保留原文字,让模型知道用户想重试什么
        launchSend(
            userMsg.intent,
            if (userMsg.intent == Intents.Id.PROFICIENCY) userMsg.text else "",
        )
    }

    fun consumeToast() = update { it.copy(toast = null) }

    // ---- 发送管线 ----

    /**
     * 熟练度分析授权:只属于发起那一次「页 + 会话 + 绑定代际」。
     * 任何真实的页/会话导航(切页、切会话、清空、删除当前会话)都会递增代际,
     * 在途分析随之永久废止——离开后再回到同一页同一会话也不会复活。
     */
    private data class ProficiencyGrant(
        val page: PageRef,
        val sessionId: String,
        val epoch: Long,
    )

    /** 导航换绑:代际递增与 session 切换在同一临界区,在途授权分析随之永久失效 */
    private fun rebindSession(target: AgentSession) {
        synchronized(bindingLock) {
            bindingEpoch++
            session = target
        }
    }

    /**
     * 授权是否仍是发起那一次绑定;普通对话(grant=null)恒为真。
     * 代际是主判定:导航只增不减,所以授权一旦失效就不会被「回到原页原会话」重新激活。
     */
    private fun grantActive(grant: ProficiencyGrant?): Boolean {
        if (grant == null) return true
        if (bindingEpoch != grant.epoch) return false
        if (session?.id != grant.sessionId) return false
        val bound = _state.value.boundPage ?: return false
        return bound.notebookId == grant.page.notebookId && bound.pageId == grant.page.pageId
    }

    /** 落盘前的复验(需持有 [bindingLock]):代际与会话身份都必须仍是发起时那份快照 */
    private fun grantStillBoundLocked(grant: ProficiencyGrant, snapshot: AgentSession): Boolean =
        bindingEpoch == grant.epoch && snapshot.id == grant.sessionId

    private fun launchSend(intent: Intents.Id?, extraText: String, grant: ProficiencyGrant? = null) {
        val s = _state.value
        val page = s.boundPage
        val grantPage = grant?.page
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
            // 没有本次授权时不使用「已授权」模板(例如重试),避免模型误以为拿到了时序数据
            ?.takeIf { it.id != Intents.Id.PROFICIENCY || grantPage != null }
        val intentForChips = intent
        scope.launch {
            // 授权分析:协程还没跑起来就换了页/会话(连点申请、立即切页)→ 直接撤销,不留消息
            if (!grantActive(grant)) return@launch
            val phase0 = _state.value
            val drafts = phase0.draftAttachments
            // 1. 视觉图拍平:多页文档按逐页展开(每页一条),单图草稿一条
            val visuals = ArrayList<VisualDraft>()
            for (d in drafts) {
                if (d.extraPages.isNotEmpty()) {
                    val base = d.mentionName.ifBlank { d.logicalName }
                    for (p in d.extraPages) {
                        visuals.add(VisualDraft(d, p, if (base.isBlank()) "" else "@$base 第${p.pageNumber}页"))
                    }
                } else if (d.png.isNotEmpty()) {
                    visuals.add(VisualDraft(d, null, if (d.mentionName.isBlank()) "" else "@${d.mentionName}"))
                }
            }
            val capped = ImageBudget.truncate(visuals, ImageBudget.maxImagesFor(model))
            if (capped.second > 0) {
                update { it.copy(toast = "已达单请求图上限,已截去最早 ${capped.second} 张") }
            }
            val finalVisuals = capped.first

            // 2. 附件落盘(发送时才写,§4.3):按草稿粒度聚合(多页文档保留 pageFiles)
            val keptDraftIds = finalVisuals.map { it.draft.id }.toSet()
            val atts = drafts
                .filter { it.id in keptDraftIds || it.textContent.isNotBlank() }
                .map { store.storeRichAttachment(page.notebookId, it) }

            // 3. 文档正文块(逐份封顶;全文已落盘,可经 readAttachmentText 读回)
            val docBlocks = drafts
                .filter { it.textContent.isNotBlank() }
                .map { d ->
                    val mention = d.mentionName.ifBlank { d.logicalName }
                    val range = if (d.pageRangeLabel.isNotBlank()) " (${d.pageRangeLabel})" else ""
                    "[附件文档 @$mention$range]" to d.textContent.take(MAX_DOC_CHARS)
                }

            // 4. 消息入状态
            val userMsg = UiMessage(
                id = UUID.randomUUID().toString().substring(0, 8),
                role = AgentMessage.Role.USER,
                text = extraText.ifBlank {
                    intent?.let { "[${Intents.byId(it).label}]" } ?: ""
                },
                attachments = atts,
                intent = intent,
                anchor = finalVisuals.firstOrNull { it.draft.anchor != null }?.draft?.anchor,
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

            // 5. 请求(历史只带文字,图在最后一条 user;§6.2)
            //    [图N] 前缀始终按图数生成;只有用户文字里出现 @ 提及才补图名映射,
            //    避免普通请求被「图片对应:…」污染
            val history = historyWindow()
            val userWire = if (spec != null) PromptEngine.userText(spec, extraText) else extraText
            val mentionsUsed = userWire.contains("@") || extraText.contains("@")
            val labels = finalVisuals.map { if (mentionsUsed) it.label else "" }
            // 工具与技能:按用户开关生成 wire 声明,技能只注入目录(正文按需 read_skill);
            // 授权类工具(时序)只在本 turn 的显式申请内出现
            val authorizedTools = if (grantPage != null) {
                setOf(BuiltinTools.ELEMENT_TIMESTAMPS_TOOL_ID)
            } else {
                emptySet()
            }
            val wireTools = toolRegistry.effectiveWireTools(_state.value.toolConfigs, authorizedTools)
            val enabledSkills = _state.value.skills.filter { it.enabled }
            val requestedIntent = intent ?: lastIntent() ?: Intents.Id.EXPLAIN
            // 普通 turn 不隐式延续熟练度分析:没有本次授权时,即使上一条是分析意图也回落到讲解
            val promptSpec = if (requestedIntent == Intents.Id.PROFICIENCY && grantPage == null) {
                Intents.byId(Intents.Id.EXPLAIN)
            } else {
                Intents.byId(requestedIntent)
            }
            val req = ChatRequest(
                model = model.id,
                system = PromptEngine.systemPrompt(promptSpec, enabledSkills, wireTools, grantPage),
                history = history,
                newUserText = PromptEngine.assembleRichUserContent(userWire, labels, docBlocks),
                images = finalVisuals.map { java.util.Base64.getEncoder().encodeToString(it.bytes) },
                imageMediaTypes = finalVisuals.map { if (it.jpeg) "image/jpeg" else "image/png" },
                imageStyle = profile.imageStyle,
                reasoningEffort = runCatching { ReasoningEffort.valueOf(profile.reasoningEffort) }
                    .getOrDefault(ReasoningEffort.AUTO),
                // 会话标识:OpenCode Go 用 x-opencode-session 归集会话(缺省按 profile 派生)
                sessionId = session?.id ?: "milknote-${profile.id}",
                tools = wireTools,
            )
            runExchange(profile, model, req, userMsg, asstMsg, intentForChips, grant)
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

    /** 流式交换 + §11 重试策略(自动重试仅首 token 前)+ 多轮工具调用(≤ MAX_TOOL_ROUNDS) */
    private fun runExchange(
        profile: ProviderProfile,
        model: ModelInfo,
        req: ChatRequest,
        userMsg: UiMessage,
        asstMsg: UiMessage,
        intent: Intents.Id?,
        grant: ProficiencyGrant? = null,
    ) {
        // 模型级协议覆盖(OpenCode Go 一家多协议):请求前把 profile 的默认协议替换为实际协议
        val effectiveProfile = profile.copy(protocol = profile.effectiveProtocol(model.id))
        val proficiencyGrantPage = grant?.page
        sendJob = scope.launch {
            // 授权分析:调度间隙又换了页/会话 → 连请求都不发(消息随切页已从当前会话移除)
            if (!grantActive(grant)) return@launch
            var attempt = 0
            val text = StringBuilder()
            val thinking = StringBuilder()
            val traces = ArrayList<ToolTrace>()
            var usagePrompt = 0
            var usageCompletion = 0
            var request = req
            var round = 0
            generationStartedAt = clockMs()
            generationFirstTokenAt = 0L

            // 授权分析换页/换会话后即撤销:不再改当前会话 UI、不读时序、不落盘、不发下一轮
            fun granted(): Boolean = grantActive(grant)

            fun replaceAssistant(transform: (UiMessage) -> UiMessage) {
                if (!granted()) return
                update { s ->
                    s.copy(messages = s.messages.map { if (it.id == asstMsg.id) transform(it) else it })
                }
            }

            fun tracesSnapshot(): List<ToolTrace> = traces.toList()

            fun applyTraces() {
                val snapshot = tracesSnapshot()
                replaceAssistant { it.copy(toolTraces = snapshot) }
            }

            fun accumulatedUsage(): ChatEvent.Usage? =
                if (usagePrompt > 0 || usageCompletion > 0) {
                    ChatEvent.Usage(usagePrompt, usageCompletion)
                } else {
                    null
                }

            fun successUsage(): ChatEvent.Usage = accumulatedUsage() ?: ChatEvent.Usage(
                ImageBudget.estTokens(req.images.size, req.newUserText.length),
                (text.length / 1.6).toInt(),
            )

            fun finishDone() {
                if (!granted()) return
                val usage = successUsage()
                val genMs = measuredGenerationMs(generationStartedAt, generationFirstTokenAt, usage.completionTokens)
                val snapshot = tracesSnapshot()
                replaceAssistant {
                    it.copy(
                        text = text.toString(),
                        reasoning = thinking.toString(),
                        streaming = false,
                        phase = AgentMessage.Phase.DONE,
                        promptTokens = usage.promptTokens,
                        completionTokens = usage.completionTokens,
                        generationMs = genMs,
                        toolTraces = snapshot,
                    )
                }
                if (granted()) update { it.copy(phase = AgentMessage.Phase.DONE) }
                persistWith(
                    userMsg, asstMsg, text.toString(), AgentMessage.Phase.DONE, intent, usage,
                    model.id, thinking.toString(), genMs, usage.promptTokens, usage.completionTokens, snapshot,
                    grant = grant,
                )
            }

            fun finishFailed(error: AgentError) {
                if (!granted()) return
                val usage = accumulatedUsage()
                val genMs = measuredGenerationMs(
                    generationStartedAt, generationFirstTokenAt, usage?.completionTokens ?: 0,
                )
                val snapshot = tracesSnapshot()
                replaceAssistant {
                    it.copy(
                        text = text.toString(),
                        reasoning = thinking.toString(),
                        streaming = false,
                        phase = AgentMessage.Phase.FAILED,
                        generationMs = genMs,
                        toolTraces = snapshot,
                    )
                }
                if (granted()) update { it.copy(phase = AgentMessage.Phase.FAILED, error = error) }
                persistWith(
                    userMsg, asstMsg, text.toString(), AgentMessage.Phase.FAILED, intent, usage,
                    model.id, thinking.toString(), genMs,
                    usage?.promptTokens ?: 0, usage?.completionTokens ?: 0, snapshot,
                    grant = grant,
                )
            }

            fun finishInterrupted() {
                if (!granted()) return
                val usage = accumulatedUsage()
                val genMs = measuredGenerationMs(
                    generationStartedAt, generationFirstTokenAt, usage?.completionTokens ?: 0,
                )
                val snapshot = tracesSnapshot()
                replaceAssistant {
                    it.copy(
                        text = text.toString(),
                        reasoning = thinking.toString(),
                        streaming = false,
                        phase = AgentMessage.Phase.INTERRUPTED,
                        generationMs = genMs,
                        toolTraces = snapshot,
                    )
                }
                if (granted()) update { it.copy(phase = AgentMessage.Phase.INTERRUPTED) }
                persistWith(
                    userMsg, asstMsg, text.toString(), AgentMessage.Phase.INTERRUPTED, intent, usage,
                    model.id, thinking.toString(), genMs,
                    usage?.promptTokens ?: 0, usage?.completionTokens ?: 0, snapshot,
                    grant = grant,
                )
            }

            while (true) {
                val roundStart = text.length
                val roundCalls = ArrayList<ToolCall>()
                val provider = providerFactory(effectiveProfile, { keyFor(effectiveProfile.apiKeyAlias) }) { patch ->
                    // 兼容性自适配回写(§3.5:imageStyle / streamOptions),落盘后续不再抖动
                    store.saveProfiles(currentBook().let { b ->
                        b.copy(profiles = b.profiles.map {
                            if (it.id == effectiveProfile.id) it.copy(
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
                var roundUsage: ChatEvent.Usage? = null
                try {
                    provider.chat(request).collect { ev ->
                        when (ev) {
                            is ChatEvent.Finish -> {}   // 完成信号仅记录;Done 在 [DONE] 或流关闭时发
                            is ChatEvent.Thinking -> {
                                gotDelta = true   // 思考也是已计费内容:不进自动重试(§11)
                                if (generationFirstTokenAt == 0L) generationFirstTokenAt = clockMs()
                                thinking.append(ev.text)
                                replaceAssistant { it.copy(reasoning = thinking.toString(), streaming = true) }
                            }
                            is ChatEvent.Delta -> {
                                gotDelta = true
                                if (generationFirstTokenAt == 0L) generationFirstTokenAt = clockMs()
                                text.append(ev.text)
                                replaceAssistant { it.copy(text = text.toString(), streaming = true) }
                            }
                            is ChatEvent.ToolCallReady -> {
                                gotDelta = true   // 工具调用同样是已计费内容
                                roundCalls.add(ev.call)
                            }
                            is ChatEvent.Usage -> roundUsage = ev
                            is ChatEvent.Done -> done = ev
                            is ChatEvent.Error -> err = ev.error
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                }

                val roundText = text.substring(roundStart)

                // 正常收尾(含服务端未发 [DONE] 的防御分支)
                if (done != null || err == null) {
                    usagePrompt += roundUsage?.promptTokens ?: 0
                    usageCompletion += roundUsage?.completionTokens ?: 0

                    if (roundCalls.isEmpty()) {
                        finishDone()
                        return@launch
                    }

                    if (round >= MAX_TOOL_ROUNDS) {
                        if (text.isEmpty()) text.append("已达到工具调用轮次上限,未能继续完成。")
                        update { it.copy(toast = "工具调用轮次已达上限") }
                        finishDone()
                        return@launch
                    }

                    // 授权分析在流式期间换页/换会话:彻底撤销,不读时序、不发下一轮、不落盘
                    if (!granted()) return@launch
                    round++
                    attempt = 0
                    val continuation = ArrayList<WireMessage>(1 + roundCalls.size)
                    continuation.add(WireMessage("assistant", roundText, toolCalls = roundCalls))
                    for (call in roundCalls) {
                        val output = executeToolCall(
                            call, traces, proficiencyGrantPage, { granted() },
                        ) { applyTraces() }
                        // 工具执行或确认等待期间换了页/会话:原始数据不再流向任何新一轮
                        if (!granted()) return@launch
                        continuation.add(
                            WireMessage("tool", output, toolCallId = call.id, toolName = call.name),
                        )
                    }
                    applyTraces()
                    replaceAssistant { it.copy(text = text.toString(), streaming = true) }
                    request = request.copy(
                        continuationMessages = request.continuationMessages + continuation,
                    )
                    continue
                }

                val e = err ?: return@launch

                // 取消
                if (e is AgentError.Cancelled) {
                    finishInterrupted()
                    return@launch
                }

                // 已有内容 → 不自动重试(不重复计费,§11),但保留真实错误:
                // content_filter / 参数错 / 网络断 一律原样上报,只有真·断流才是 StreamBroken
                if (gotDelta || roundText.isNotEmpty()) {
                    finishFailed(e)
                    return@launch
                }

                // ModelNotVision 连坐(§3.4):2 次后本地标 vision=false
                if (e is AgentError.ModelNotVision) {
                    val key = "${effectiveProfile.id}/${model.id}"
                    val n = (notVisionStrikes[key] ?: 0) + 1
                    notVisionStrikes[key] = n
                    if (n >= 2) {
                        val marked = currentBook().let { b ->
                            b.copy(profiles = b.profiles.map { p ->
                                if (p.id == effectiveProfile.id) p.copy(
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

                finishFailed(e)
                return@launch
            }
        }
    }

    /**
     * 执行单个工具调用:未注册/已关闭 → 回传错误;需确认 → 挂起等待 UI;
     * 拒绝 → DENIED;执行后写 DONE/ERROR 并返回给模型的文本结果。
     * [grantActive] 为授权分析的存活判定:切页/换会话后连工具都不再执行。
     */
    private suspend fun executeToolCall(
        call: ToolCall,
        traces: MutableList<ToolTrace>,
        grantPage: PageRef?,
        grantActive: () -> Boolean = { true },
        onTracesChanged: () -> Unit,
    ): String {
        val tool = toolRegistry.get(call.name)
        if (tool == null) {
            traces += ToolTrace(
                callId = call.id,
                toolName = call.name,
                displayName = call.name,
                argumentsJson = call.argumentsJson,
                status = ToolTrace.Status.ERROR,
                resultSummary = "未知工具",
                output = "工具不存在",
            )
            onTracesChanged()
            return "工具 ${call.name} 不存在;可用工具:${toolRegistry.allSpecs().joinToString(", ") { it.id }}。" +
                "请直接回答,不要重复调用。"
        }

        val spec = tool.spec
        // 授权分析在切页/换会话后立即失效:任何工具都不再执行(不读、不改当前页面)
        if (!grantActive()) {
            return "用户的熟练度分析授权已随页面/会话切换撤销,本次不再读取或修改任何页面数据。请直接回答。"
        }
        val config = effectiveConfigOf(spec)
        val displayName = toolRegistry.effectiveDisplayName(spec, config)
        val args = ToolArgs.parse(call.argumentsJson)
        val normalizedArgs = ToolArgs.encode(args)
        val action = runCatching { tool.describeAction(args, toolContext(config, grantPage)) }
            .getOrDefault(displayName)

        // 授权类工具的执行硬门禁:即使模型伪造调用,没有本 turn 授权也不触达页面
        if (spec.requiresUserGrant && grantPage == null) {
            traces += ToolTrace(
                callId = call.id,
                toolName = spec.id,
                displayName = displayName,
                argumentsJson = normalizedArgs,
                status = ToolTrace.Status.ERROR,
                actionSummary = action,
                resultSummary = "未获授权",
                output = "未获授权",
            )
            onTracesChanged()
            return "工具「$displayName」只在用户本轮显式申请熟练度分析时可用;本轮未获得授权," +
                "不能读取页面时间数据。请提示用户点击「申请熟练度分析」后再试,不要重复调用。"
        }

        if (!config.enabled) {
            traces += ToolTrace(
                callId = call.id,
                toolName = spec.id,
                displayName = displayName,
                argumentsJson = normalizedArgs,
                status = ToolTrace.Status.ERROR,
                actionSummary = action,
                resultSummary = "工具已关闭",
                output = "工具已关闭",
            )
            onTracesChanged()
            return "工具「$displayName」已被用户关闭,无法使用。请直接回答,不要重复调用。"
        }

        var approved = !toolRegistry.requiresConfirmation(spec, config)
        traces += ToolTrace(
            callId = call.id,
            toolName = spec.id,
            displayName = displayName,
            argumentsJson = normalizedArgs,
            status = if (approved) ToolTrace.Status.RUNNING else ToolTrace.Status.PENDING_CONFIRM,
            actionSummary = action,
        )
        onTracesChanged()

        if (!approved) {
            val pending = PendingToolConfirmation(
                callId = call.id,
                toolId = spec.id,
                displayName = displayName,
                actionSummary = action,
                argumentsJson = normalizedArgs,
            )
            val deferred = CompletableDeferred<Boolean>()
            confirmDeferred = deferred
            update { it.copy(pendingConfirmation = pending) }
            approved = try {
                deferred.await()
            } finally {
                if (confirmDeferred === deferred) confirmDeferred = null
                update { it.copy(pendingConfirmation = null) }
            }
            if (!approved) {
                updateTrace(traces, call.id) {
                    it.copy(status = ToolTrace.Status.DENIED, resultSummary = "用户已取消")
                }
                onTracesChanged()
                return "用户取消了「$action」,该操作未执行。不要重复调用同一操作;请说明原因或提供替代方案。"
            }
            updateTrace(traces, call.id) { it.copy(status = ToolTrace.Status.RUNNING) }
            onTracesChanged()
        }

        // 授权在等待确认期间被撤销(换页/换会话):任何工具都不再对当前页面生效
        if (!grantActive()) {
            updateTrace(traces, call.id) {
                it.copy(status = ToolTrace.Status.DENIED, resultSummary = "授权已撤销", output = "")
            }
            onTracesChanged()
            return "本次熟练度分析的授权已随页面/会话切换撤销,该操作未执行,页面时间数据也不再读取。" +
                "请直接回答用户当前页面的问题。"
        }

        val result = try {
            tool.execute(args, toolContext(config, grantPage))
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            ToolExecResult(false, "工具执行失败:${t.message ?: t::class.simpleName}")
        }

        updateTrace(traces, call.id) {
            it.copy(
                status = if (result.ok) ToolTrace.Status.DONE else ToolTrace.Status.ERROR,
                resultSummary = result.summary.ifBlank { if (result.ok) "完成" else "失败" },
                // 时序类工具只把原始数据回传本次调用链;轨迹/历史仅保留计数摘要
                output = if (spec.recordOutputInTrace) result.output.take(TOOL_OUTPUT_MAX) else "",
            )
        }
        onTracesChanged()
        return if (result.ok) {
            result.output.take(TOOL_OUTPUT_MAX)
        } else {
            "工具执行失败:${result.output.take(TOOL_OUTPUT_MAX)}"
        }
    }

    private fun updateTrace(
        traces: MutableList<ToolTrace>,
        callId: String,
        transform: (ToolTrace) -> ToolTrace,
    ) {
        val index = traces.indexOfFirst { it.callId == callId }
        if (index >= 0) traces[index] = transform(traces[index])
    }

    private fun toolContext(config: ToolConfig, grantPage: PageRef? = null) = ToolContext(
        boundPage = _state.value.boundPage,
        config = config,
        workspace = workspace,
        skillStore = skillManager,
        webSearch = webSearch,
        elementTimestampsPage = grantPage,
    )

    /** 首 token → 完成耗时;单块快速返回时按 50ms 兜底(速度不至于除零) */
    private fun measuredGenerationMs(startedAt: Long, firstTokenAt: Long, completionTokens: Int): Long {
        val from = if (firstTokenAt > 0L) firstTokenAt else startedAt
        val elapsed = (clockMs() - from).coerceAtLeast(0L)
        return if (completionTokens > 0 && elapsed <= 0L) 50L else elapsed
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
        generationMs: Long = 0L,
        promptTokens: Int = usage?.promptTokens ?: 0,
        completionTokens: Int = usage?.completionTokens ?: 0,
        toolTraces: List<ToolTrace> = emptyList(),
        grant: ProficiencyGrant? = null,
    ) {
        // 写回只使用调用时读到的会话快照;授权分析还要在同一临界区里复验代际,
        // 避免 guard 通过后被主线程切页,把旧页的分析写进碰巧换到的新会话目录。
        fun writeExchange(s: AgentSession): AgentSession {
            val now = clockMs()
            val u = AgentMessage(
                id = userMsg.id, role = AgentMessage.Role.USER, sentAt = now,
                text = userMsg.text, attachments = userMsg.attachments, intent = intent?.name,
            )
            val a = AgentMessage(
                id = asstMsg.id, role = AgentMessage.Role.ASSISTANT, sentAt = now,
                text = asstText, reasoning = thinkingText, modelId = modelId,
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                phase = phase.name,
                generationMs = generationMs,
                toolTraces = toolTraces,
            )
            val messages = (s.messages.filter { it.id != userMsg.id && it.id != asstMsg.id } + u + a)
                .sortedBy { it.sentAt }
            val title = s.title.ifBlank {
                messages.firstOrNull { it.role == AgentMessage.Role.USER && it.text.isNotBlank() }
                    ?.text?.lineSequence()?.firstOrNull()?.trim()?.take(40) ?: ""
            }
            val withExchange = s.copy(
                messages = messages,
                updatedAt = now,
                providerId = _state.value.activeProfileId ?: s.providerId,
                modelId = modelId,
                title = title,
            )
            store.saveSession(withExchange)
            // 会话内消息状态同步(重试/清空后的内存视图)
            val uiById = _state.value.messages.associateBy { it.id }
            val synced = withExchange.copy(
                messages = withExchange.messages.map { m ->
                    val ui = uiById[m.id]
                    if (ui != null) m.copy(insertedAnnotationId = ui.insertedAnnotationId) else m
                },
            )
            session = synced
            return synced
        }

        val saved = if (grant == null) {
            writeExchange(session ?: return)
        } else {
            synchronized(bindingLock) {
                val snapshot = session
                if (snapshot == null || !grantStillBoundLocked(grant, snapshot)) null
                else writeExchange(snapshot)
            }
        } ?: return
        refreshSessions()
        // 首轮完成/每 3 轮增量/离开会话时后台刷新摘要
        maybeScheduleAutoSummary(saved)
    }

    private fun persistSession() {
        val s = session ?: return
        val messages = s.messages + _state.value.messages
            .filter { ui -> s.messages.none { it.id == ui.id } }
            .map { ui ->
                AgentMessage(
                    id = ui.id, role = ui.role, sentAt = clockMs(), text = ui.text,
                    attachments = ui.attachments, intent = ui.intent?.name, modelId = ui.modelId,
                    promptTokens = ui.promptTokens, completionTokens = ui.completionTokens,
                    phase = ui.phase.name, generationMs = ui.generationMs,
                    toolTraces = ui.toolTraces,
                )
            }
        session = s.copy(messages = messages, updatedAt = clockMs())
        store.saveSession(session!!)
        refreshSessions()
    }

    // ==================== AI 会话摘要(后台生成,不进聊天区) ====================

    private data class SummaryTarget(val profile: ProviderProfile, val model: ModelInfo)

    /**
     * 自动摘要时机(任一满足):
     * - 首轮完成且尚无摘要
     * - 每新增 6 条已完成消息(≈3 轮)增量刷新
     * - 离开会话(切会话/清空)且摘要落后于已完成消息
     */
    private fun maybeScheduleAutoSummary(target: AgentSession, leaving: Boolean = false) {
        if (!_state.value.summaryConfig.enabled) return
        if (summaryJobs[target.id]?.isActive == true) return
        val completedAll = target.messages.count {
            it.phase == AgentMessage.Phase.DONE.name && it.text.isNotBlank()
        }
        val completedAsst = target.messages.count {
            it.role == AgentMessage.Role.ASSISTANT &&
                it.phase == AgentMessage.Phase.DONE.name &&
                it.text.isNotBlank()
        }
        if (completedAsst < 1 || completedAll < 2) return
        val due = when {
            target.summary.isBlank() -> true
            leaving -> completedAll > target.summaryMessageCount
            else -> completedAll - target.summaryMessageCount >= 6
        }
        if (!due) return
        scheduleSummary(target)
    }

    private fun scheduleSummary(target: AgentSession, force: Boolean = false) {
        if (!force && !_state.value.summaryConfig.enabled) return
        if (summaryJobs[target.id]?.isActive == true) return
        val resolved = resolveSummaryTarget(target)
        if (resolved == null) {
            // 手动刷新必须给反馈(自动摘要保持静默)
            if (force) update { it.copy(toast = "暂无可用模型或 API Key") }
            return
        }
        // 手动刷新允许带上失败/半截消息作素材(自动摘要只用已完成内容)
        val transcript = summaryTranscript(target, includeFailed = force)
        if (transcript.isBlank()) return
        update { it.copy(summarizingSessionIds = it.summarizingSessionIds + target.id) }
        refreshSessions()   // 立即把 summaryGenerating 反映到列表
        // 模型级协议覆盖对摘要同样生效(OpenCode Go:摘要模型可能走别的协议)
        val effectiveProfile = resolved.profile.copy(
            protocol = resolved.profile.effectiveProtocol(resolved.model.id),
        )
        val job = scope.launch {
            var summaryError: AgentError? = null
            try {
                val req = ChatRequest(
                    model = resolved.model.id,
                    system = SUMMARY_SYSTEM_PROMPT,
                    newUserText = transcript,
                    stream = true,
                    temperature = 0.2,
                    maxTokens = 160,
                    // 摘要只要一句标题:能关思考就显式关(MiMo 等会回 thinking.disabled),
                    // 免得推理模型的思考把输出预算吃光、正文为空
                    reasoningEffort = if (ReasoningEffort.OFF.name in resolved.model.thinkingLevels) {
                        ReasoningEffort.OFF
                    } else {
                        ReasoningEffort.AUTO
                    },
                    sessionId = "milknote-summary-${target.id}",
                )
                val out = StringBuilder()
                val provider = providerFactory(effectiveProfile, { keyFor(effectiveProfile.apiKeyAlias) }, {})
                provider.chat(req).collect { ev ->
                    when (ev) {
                        is ChatEvent.Delta -> out.append(ev.text)
                        is ChatEvent.Error -> summaryError = ev.error
                        else -> Unit
                    }
                }
                val summary = cleanSummary(out.toString())
                if (summary.isNotBlank()) {
                    applySummary(target, summary, resolved.model.id)
                } else if (force) {
                    update { it.copy(toast = "总结生成失败：${summaryFailureReason(summaryError)}") }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                // 摘要纯属锦上添花:自动路径失败静默,手动路径必须给出原因
                if (force) {
                    update { it.copy(toast = "总结生成失败：${summaryFailureReason(ErrorMapping.fromException(t))}") }
                }
            } finally {
                summaryJobs.remove(target.id)
                update { it.copy(summarizingSessionIds = it.summarizingSessionIds - target.id) }
                // applySummary 里的 refreshSessions 早于本处清理,必须再同步一次,
                // 否则列表会残留 summaryGenerating=true
                refreshSessions()
            }
        }
        summaryJobs[target.id] = job
    }

    /** 摘要失败的提示文案:取错误首行(正文可能多行),最长 80 字 */
    private fun summaryFailureReason(error: AgentError?): String =
        error?.detail?.lineSequence()?.firstOrNull { it.isNotBlank() }?.take(80) ?: "未返回内容"

    /** 摘要模型:CUSTOM 优先(允许非视觉模型);任何一步失效都回退到跟随聊天模型 */
    private fun resolveSummaryTarget(target: AgentSession): SummaryTarget? {
        val s = _state.value
        val cfg = s.summaryConfig
        if (cfg.mode == SummaryModelMode.CUSTOM) {
            val profile = cfg.profileId?.let { id -> s.profiles.firstOrNull { it.id == id } }
            val model = cfg.modelId?.let { mid -> profile?.models?.firstOrNull { it.id == mid } }
            if (profile != null && model != null && hasKey(profile)) return SummaryTarget(profile, model)
        }
        val active = s.profiles.firstOrNull { it.id == s.activeProfileId } ?: s.profiles.firstOrNull()
        val activeModel = active?.activeModel()
        if (active != null && activeModel != null && hasKey(active)) {
            return SummaryTarget(active, activeModel)
        }
        val fromSession = s.profiles.firstOrNull { it.id == target.providerId }
        if (fromSession != null) {
            val model = fromSession.models.firstOrNull { it.id == target.modelId } ?: fromSession.activeModel()
            if (model != null && hasKey(fromSession)) return SummaryTarget(fromSession, model)
        }
        return null
    }

    /**
     * 压缩对话为摘要输入(首轮 + 最近几轮,≤3k 字符)。
     * 手动刷新([includeFailed])在「一条已完成助手消息都没有」时放宽到全部消息,
     * 否则刚失败/刚中断的会话点「重新生成」会永远拿不到素材。
     */
    private fun summaryTranscript(target: AgentSession, includeFailed: Boolean = false): String {
        var msgs = target.messages.filter {
            it.phase != AgentMessage.Phase.FAILED.name && it.text.isNotBlank()
        }
        if (includeFailed && msgs.none { it.role == AgentMessage.Role.ASSISTANT }) {
            msgs = target.messages.filter { it.text.isNotBlank() }
        }
        if (msgs.isEmpty()) return ""
        val picked = (msgs.take(2) + msgs.takeLast(6)).distinctBy { it.id }
        val sb = StringBuilder()
        for (m in picked) {
            val role = if (m.role == AgentMessage.Role.USER) "用户" else "助手"
            sb.append(role).append(':').append(m.text.trim().replace('\n', ' ')).append('\n')
        }
        // 显式用户侧框架:单靠 system 提示时,模型常把「对话里有问题」理解成「要回答它」
        return (
            "请为以下对话生成一句不超过 30 字的中文标题（只输出标题本身，不要回答对话中的问题）：\n" +
                "---\n" + sb + "---"
            ).take(SUMMARY_TRANSCRIPT_MAX)
    }

    /**
     * 摘要清洗(模型不一定听话:可能回 Markdown 标题、多行解释、思考标签)。
     * 顺序:剥 think 块 → 取首个非空行 → 去列表/编号 → 去 Markdown 标记 → 去前缀 → 去引号句号。
     */
    private fun cleanSummary(raw: String): String {
        var t = raw.replace(
            Regex("<think(?:ing)?>[\\s\\S]*?</think(?:ing)?>", RegexOption.IGNORE_CASE),
            " ",
        )
        t = t.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: ""
        t = t.replace(Regex("^[#>*\\-•\\s]+"), "")
        t = t.replace(Regex("^\\d+[.)、]\\s*"), "")
        t = t.replace("**", "").replace("__", "").replace("`", "")
        t = t.replace(Regex("^(标题|摘要|总结|主题)\\s*[:：]\\s*"), "")
        t = t.replace(Regex("\\s+"), " ").trim()
        t = t.trim('"', '\'', '“', '”', '‘', '’', '「', '」', '《', '》', '。', '，', ',', '.', '：', ':').trim()
        if (t.length > SUMMARY_MAX_CHARS) t = t.take(SUMMARY_MAX_CHARS)
        return t.trim()
    }

    /** 写入摘要:优先合并内存里的最新会话,避免覆盖并发新增的聊天消息 */
    private fun applySummary(target: AgentSession, summary: String, modelId: String) {
        val active = session
        val latest = if (active != null && active.id == target.id) {
            active
        } else {
            store.loadSession(target.notebookId, target.id) ?: return
        }
        val completedAll = latest.messages.count {
            it.phase == AgentMessage.Phase.DONE.name && it.text.isNotBlank()
        }
        val updated = latest.copy(
            summary = summary,
            summaryUpdatedAt = clockMs(),
            summaryMessageCount = completedAll,
            summaryModelId = modelId,
        )
        if (active != null && active.id == updated.id) session = updated
        store.saveSession(updated)
        refreshSessions()
    }

    private fun cancelSummary(sessionId: String) {
        summaryJobs.remove(sessionId)?.cancel()
        update { it.copy(summarizingSessionIds = it.summarizingSessionIds - sessionId) }
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

    /** 上下文规模预估(附件区「≈ N tok」;多页文档按逐页图计) */
    fun estTokensNow(): Int {
        val s = _state.value
        val images = s.draftAttachments.sumOf { d ->
            when {
                d.extraPages.isNotEmpty() -> d.extraPages.size
                d.png.isNotEmpty() -> 1
                else -> 0
            }
        }
        val text = s.draftText.length + s.draftAttachments.sumOf { it.textContent.length }
        return ImageBudget.estTokens(images, text)
    }

    companion object {
        /** 单次发送最多工具轮次(防止模型无限调用) */
        const val MAX_TOOL_ROUNDS = 8

        /** 回传模型的单个工具输出上限(字符) */
        const val TOOL_OUTPUT_MAX = 8000
    }
}
