import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import type { Components } from 'react-markdown'

interface MarkdownRendererProps {
  content: string
  className?: string
}

// Custom image component that handles attachment:// URLs
const attachmentImageRenderer: Components['img'] = ({ src, alt, ...props }) => {
  if (src?.startsWith('attachment://')) {
    const attachmentId = src.replace('attachment://', '')
    const downloadUrl = `/api/v1/attachments/${attachmentId}/download`
    return <img src={downloadUrl} alt={alt ?? ''} style={{ maxWidth: '100%' }} {...props} />
  }
  return <img src={src} alt={alt ?? ''} style={{ maxWidth: '100%' }} {...props} />
}

// Custom link component that handles attachment:// URLs
const attachmentLinkRenderer: Components['a'] = ({ href, children, ...props }) => {
  if (href?.startsWith('attachment://')) {
    const attachmentId = href.replace('attachment://', '')
    const downloadUrl = `/api/v1/attachments/${attachmentId}/download`
    return (
      <a href={downloadUrl} target="_blank" rel="noopener noreferrer" {...props}>
        {children}
      </a>
    )
  }
  return (
    <a href={href} target="_blank" rel="noopener noreferrer" {...props}>
      {children}
    </a>
  )
}

export function MarkdownRenderer({ content, className }: MarkdownRendererProps) {
  return (
    <div className={className} style={{ wordBreak: 'break-word', lineHeight: 1.6 }}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          img: attachmentImageRenderer,
          a: attachmentLinkRenderer,
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  )
}
