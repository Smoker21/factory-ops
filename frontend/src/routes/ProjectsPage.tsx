import {
  Title,
  Stack,
  Button,
  Group,
  TextInput,
  Select,
  SimpleGrid,
  Skeleton,
  Text,
  Modal,
} from '@mantine/core'
import { useDisclosure } from '@mantine/hooks'
import { useState } from 'react'
import { useForm, FormProvider } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'
import { notifications } from '@mantine/notifications'
import { ProjectCard } from '@/components/project/ProjectCard'
import { ProjectFromTemplateModal } from '@/components/project/ProjectFromTemplateModal'
import { useProjects, useCreateProject } from '@/hooks/useProjects'
import { useAuth } from '@/auth/useAuth'
import { canCreateProject } from '@/rbac/rbacGuards'
import { extractProblemMessage } from '@/lib/utils'
import type { ProjectTemplate } from '@/api/types'

const createSchema = z.object({
  name: z.string().min(1, '專案名稱必填').max(120),
  descriptionMarkdown: z.string().optional(),
  organizationId: z.string().min(1, '必須選擇組織'),
  ownerId: z.string().min(1, '必須指定負責人'),
  groupIds: z.string().min(1, '必須選擇群組'),
})

type CreateFormData = z.infer<typeof createSchema>

export function ProjectsPage() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const [createOpen, { open: openCreate, close: closeCreate }] = useDisclosure(false)
  const [templateModalOpen, { open: openTemplate, close: closeTemplate }] = useDisclosure(false)
  const [statusFilter, setStatusFilter] = useState<string | null>(null)
  const [searchQuery, setSearchQuery] = useState('')

  const projectsQuery = useProjects({
    status: statusFilter ?? undefined,
    q: searchQuery || undefined,
  })

  const createMutation = useCreateProject()
  const methods = useForm<CreateFormData>({ resolver: zodResolver(createSchema) })
  const {
    register,
    handleSubmit,
    setValue,
    reset,
    formState: { errors, isSubmitting },
  } = methods

  const onSubmit = async (data: CreateFormData) => {
    try {
      await createMutation.mutateAsync({
        organizationId: data.organizationId,
        name: data.name,
        descriptionMarkdown: data.descriptionMarkdown,
        ownerId: data.ownerId,
        memberIds: [data.ownerId],
        groupIds: [data.groupIds],
      })
      notifications.show({ message: '專案已建立', color: 'green' })
      reset()
      closeCreate()
    } catch (err) {
      notifications.show({ message: extractProblemMessage(err), color: 'red' })
    }
  }

  const handleTemplateSelect = (template: ProjectTemplate) => {
    setValue('name', template.name)
    setValue('descriptionMarkdown', template.descriptionMarkdown ?? '')
    openCreate()
  }

  const projects = projectsQuery.data?.items ?? []

  return (
    <Stack gap="lg">
      <Group justify="space-between" align="center">
        <Title order={2}>{t('project.projects')}</Title>
        {canCreateProject(user) && (
          <Group>
            <Button variant="outline" onClick={openTemplate} style={{ minHeight: 44 }}>
              {t('project.fromTemplate')}
            </Button>
            <Button onClick={openCreate} style={{ minHeight: 44 }}>
              {t('project.create')}
            </Button>
          </Group>
        )}
      </Group>

      <Group>
        <TextInput
          placeholder={`${t('app.search')}...`}
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.currentTarget.value)}
          style={{ flex: 1, minWidth: 200 }}
        />
        <Select
          placeholder={t('project.status')}
          data={[
            { value: 'DRAFT', label: t('project.statusDRAFT') },
            { value: 'ACTIVE', label: t('project.statusACTIVE') },
            { value: 'PAUSED', label: t('project.statusPAUSED') },
            { value: 'COMPLETED', label: t('project.statusCOMPLETED') },
            { value: 'CANCELLED', label: t('project.statusCANCELLED') },
          ]}
          value={statusFilter}
          onChange={setStatusFilter}
          clearable
          w={150}
        />
      </Group>

      {projectsQuery.isLoading && (
        <SimpleGrid cols={{ base: 1, sm: 2, lg: 3 }}>
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} height={120} radius="md" />
          ))}
        </SimpleGrid>
      )}

      {!projectsQuery.isLoading && projects.length === 0 && (
        <Text c="dimmed" ta="center" py="xl">
          {t('app.noData')}
        </Text>
      )}

      {!projectsQuery.isLoading && projects.length > 0 && (
        <SimpleGrid cols={{ base: 1, sm: 2, lg: 3 }}>
          {projects.map((project) => (
            <ProjectCard key={project.id} project={project} />
          ))}
        </SimpleGrid>
      )}

      <ProjectFromTemplateModal
        opened={templateModalOpen}
        onClose={closeTemplate}
        onSelect={handleTemplateSelect}
      />

      <Modal
        opened={createOpen}
        onClose={closeCreate}
        title={t('project.create')}
        size="lg"
        centered
      >
        <FormProvider {...methods}>
          <form onSubmit={handleSubmit(onSubmit)}>
            <Stack>
              <TextInput
                label={t('project.name')}
                required
                error={errors.name?.message}
                {...register('name')}
              />
              <TextInput
                label={t('project.description')}
                {...register('descriptionMarkdown')}
              />
              <TextInput
                label="組織 ID（leaf 組織）"
                placeholder="org-section-1"
                required
                error={errors.organizationId?.message}
                {...register('organizationId')}
              />
              <TextInput
                label="群組 ID"
                placeholder="group-1"
                required
                error={errors.groupIds?.message}
                {...register('groupIds')}
              />
              <TextInput
                label="負責人 ID"
                placeholder={user?.id}
                defaultValue={user?.id}
                required
                error={errors.ownerId?.message}
                {...register('ownerId')}
              />
              <Group justify="flex-end">
                <Button variant="outline" onClick={closeCreate}>
                  {t('app.cancel')}
                </Button>
                <Button type="submit" loading={isSubmitting} style={{ minHeight: 44 }}>
                  {t('app.create')}
                </Button>
              </Group>
            </Stack>
          </form>
        </FormProvider>
      </Modal>
    </Stack>
  )
}
