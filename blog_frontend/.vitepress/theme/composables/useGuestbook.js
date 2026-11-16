import { computed, ref } from 'vue'
import { useData } from 'vitepress'
import { fetchGuestMessages, GUEST_MESSAGE_MAX_PAGE_SIZE } from '../../../api/guest-messages.js'

/** 站点配置缺省时的每页条数。 */
const DEFAULT_PAGE_SIZE = 10

/** 运行时请求的接口前缀：与友链申请表单一样走同源相对路径。 */
const API_BASE = '/api'

/**
 * 留言板数据源：运行时从后端拉取已审核通过的留言。
 *
 * 与友链模块的差别：友链是构建期静态同步的，而留言需要审核后立即可见，
 * 所以这里走运行时接口（dev 下由 Vite 代理到后端，生产下由 nginx 反代 /api/）。
 */
export function useGuestbook() {
  const { theme } = useData()
  const site = computed(() => theme.value.site || {})
  const config = computed(() => site.value.guestbook || {})

  /**
   * 留言板开关：构建期读不到 `enableGuestbook` 时保守回退为「关闭」，
   * 此时前台不展示留言入口，直接访问 /guestbook/ 也只看到未开放提示。
   */
  const enabled = computed(() => config.value.enabled === true)

  /** 每页条数：读后台配置 `guestbookPageSize`，越界时夹紧到合法区间。 */
  const pageSize = computed(() => {
    const value = Number(config.value.pageSize)
    if (!Number.isFinite(value) || value < 1) return DEFAULT_PAGE_SIZE
    return Math.min(Math.floor(value), GUEST_MESSAGE_MAX_PAGE_SIZE)
  })

  const items = ref([])
  const page = ref(0)
  const totalElements = ref(0)
  const totalPages = ref(0)
  // 首屏默认处于「加载中」：SSR 阶段没有留言数据，先显示读取提示，
  // 客户端挂载后再拉取真实留言，避免先闪一次空态。
  const loading = ref(true)
  const error = ref('')

  /** 请求进行中的标记：防止重复触发（与 loading 的 UI 语义分开）。 */
  let inFlight = false

  async function load(targetPage = page.value) {
    if (inFlight) return

    inFlight = true
    loading.value = true
    error.value = ''

    try {
      const response = await fetchGuestMessages({
        baseUrl: API_BASE,
        page: targetPage,
        size: pageSize.value,
      })
      items.value = response.items
      page.value = response.page
      totalElements.value = response.totalElements
      totalPages.value = response.totalPages
    } catch (loadError) {
      error.value = loadError?.message || '留言加载失败，请稍后再试'
      items.value = []
      totalElements.value = 0
      totalPages.value = 0
    } finally {
      inFlight = false
      loading.value = false
    }
  }

  return {
    enabled,
    pageSize,
    items,
    page,
    totalElements,
    totalPages,
    loading,
    error,
    load,
  }
}
