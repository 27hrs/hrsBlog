import { uploadAsset, validateImageFile } from '@/api/assets.js'
import { notify } from '@/api/notify.js'

const EXT_BY_MIME = {
  'image/jpeg': 'jpg',
  'image/png': 'png',
  'image/webp': 'webp',
  'image/gif': 'gif',
}

/**
 * 裁剪上传 / 拖入等来源的 File 可能没有文件名，而后端按扩展名白名单校验；
 * 这里按 MIME 补一个扩展名，避免 415 拒绝。
 */
function ensureNamedFile(file) {
  const name = String(file?.name || '')
  if (/\.(jpe?g|png|webp|gif)$/i.test(name)) return file

  const type = String(file?.type || '').toLowerCase()
  const ext = EXT_BY_MIME[type]
  if (!ext) return file

  return new File([file], `image-${Date.now()}.${ext}`, { type: file.type })
}

/** 去掉扩展名并剔除会破坏 Markdown 图片语法的字符，作为 alt 文本。 */
function altOf(file) {
  const base = String(file?.name || '')
    .replace(/\.[^.]+$/, '')
    .replace(/[[\]()\\`\n\r]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  return base || '图片'
}

/**
 * md-editor-v3 的 `onUploadImg` 处理器（文章编辑与说说编辑共用）。
 *
 * 逐张校验并上传到站点静态资源目录（POST /api/admin/assets → /assets/image/**），
 * 成功后把返回的图片地址交回编辑器，由它在正文光标处插入 `![alt](url)` 图片链接。
 * 单张失败只提示不中断，其余图片继续上传；无论成败都会回传结果结束编辑器等待。
 */
export async function uploadEditorImages(files, callback) {
  const list = Array.from(files || [])
  const uploaded = []

  for (const file of list) {
    const candidate = ensureNamedFile(file)
    const problem = validateImageFile(candidate)
    if (problem) {
      notify(problem, 'error')
      continue
    }

    try {
      const asset = await uploadAsset(candidate)
      if (!asset?.url) throw new Error('上传响应缺少图片地址')
      uploaded.push({ url: asset.url, alt: altOf(candidate), title: '' })
    } catch (error) {
      notify(error.message || '图片上传失败', 'error')
    }
  }

  if (uploaded.length) {
    notify(uploaded.length > 1 ? `已上传 ${uploaded.length} 张图片` : '图片上传成功', 'success')
  }
  callback(uploaded)
}
