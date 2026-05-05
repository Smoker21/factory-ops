import { useState } from 'react'
import { Button, Group, Text, Stack, Progress } from '@mantine/core'
import { useTranslation } from 'react-i18next'
import {
  initiateAttachmentUpload,
  uploadToPresignedUrl,
  finalizeAttachment,
} from '@/api/attachments'
import type { Attachment } from '@/api/types'
import { extractProblemMessage } from '@/lib/utils'
import logger from '@/lib/logger'

interface AttachmentUploaderProps {
  onUploaded: (attachment: Attachment) => void
}

export function AttachmentUploader({ onUploaded }: AttachmentUploaderProps) {
  const { t } = useTranslation()
  const [isUploading, setIsUploading] = useState(false)
  const [progress, setProgress] = useState(0)
  const [error, setError] = useState<string | null>(null)

  const handleFileChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file) return

    setIsUploading(true)
    setError(null)
    setProgress(10)

    try {
      const initiated = await initiateAttachmentUpload({
        filename: file.name,
        contentType: file.type,
        sizeBytes: file.size,
      })
      setProgress(30)

      if (initiated.uploadUrl) {
        await uploadToPresignedUrl(initiated.uploadUrl, file, file.type)
      }
      setProgress(70)

      const finalized = await finalizeAttachment(initiated.id)
      setProgress(100)
      onUploaded(finalized)
    } catch (err) {
      logger.error('File upload failed', err)
      setError(extractProblemMessage(err))
    } finally {
      setIsUploading(false)
      setProgress(0)
    }
  }

  return (
    <Stack gap="xs">
      <Group>
        <Button
          component="label"
          variant="outline"
          loading={isUploading}
          size="sm"
          style={{ minHeight: 44 }}
        >
          {t('task.attachments')}
          <input
            type="file"
            hidden
            onChange={handleFileChange}
            disabled={isUploading}
          />
        </Button>
      </Group>
      {isUploading && <Progress value={progress} animated />}
      {error && <Text c="red" size="sm">{error}</Text>}
    </Stack>
  )
}
