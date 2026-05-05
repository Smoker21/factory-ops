import apiClient from './client'
import axios from 'axios'
import type { Attachment } from './types'

export interface InitiateUploadRequest {
  filename: string
  contentType: string
  sizeBytes: number
}

export async function initiateAttachmentUpload(data: InitiateUploadRequest): Promise<Attachment> {
  const response = await apiClient.post<Attachment>('/attachments', data)
  return response.data
}

export async function uploadToPresignedUrl(
  url: string,
  file: File,
  contentType: string
): Promise<void> {
  await axios.put(url, file, {
    headers: { 'Content-Type': contentType },
  })
}

export async function finalizeAttachment(attachmentId: string): Promise<Attachment> {
  const response = await apiClient.post<Attachment>(`/attachments/${attachmentId}/finalize`)
  return response.data
}

export async function getAttachment(attachmentId: string): Promise<Attachment> {
  const response = await apiClient.get<Attachment>(`/attachments/${attachmentId}`)
  return response.data
}

export async function deleteAttachment(attachmentId: string): Promise<void> {
  await apiClient.delete(`/attachments/${attachmentId}`)
}
