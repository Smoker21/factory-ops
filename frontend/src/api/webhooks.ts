import apiClient from './client'
import type { Webhook, CreateWebhookRequest } from './types'

export async function listWebhooks(): Promise<Webhook[]> {
  const response = await apiClient.get<Webhook[]>('/webhooks')
  return response.data
}

export async function createWebhook(data: CreateWebhookRequest): Promise<Webhook> {
  const response = await apiClient.post<Webhook>('/webhooks', data)
  return response.data
}

export async function deleteWebhook(webhookId: string): Promise<void> {
  await apiClient.delete(`/webhooks/${webhookId}`)
}
