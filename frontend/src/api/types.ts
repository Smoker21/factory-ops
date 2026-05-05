// Auto-aligned with openapi.yaml v1.3.0

export type Role =
  | 'OPERATOR'
  | 'SHIFT_LEAD'
  | 'ENGINEER'
  | 'QA'
  | 'GROUP_ADMIN'
  | 'GROUP_MANAGER'
  | 'ORG_MANAGER'
  | 'ORG_ADMIN'
  | 'ADMIN'

export type OrganizationType = 'FAB' | 'DIVISION' | 'DEPARTMENT' | 'SECTION'
export type GroupType = 'DEFAULT' | 'LINE' | 'TEAM' | 'SHIFT'
export type TemplateScope = 'GLOBAL' | 'ORG'
export type GroupMembershipRole = 'MEMBER' | 'LEAD' | 'OBSERVER'
export type MembershipRole = 'MEMBER' | 'LEAD' | 'OBSERVER'
export type ProjectStatus = 'DRAFT' | 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'CANCELLED'
export type TaskStatus = 'OPEN' | 'IN_PROGRESS' | 'BLOCKED' | 'IN_REVIEW' | 'DONE' | 'CANCELLED'
export type Priority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'
export type ActionRequestStatus =
  | 'SUBMITTED'
  | 'TRIAGED'
  | 'IN_PROGRESS'
  | 'RESOLVED'
  | 'REJECTED'
export type SeverityLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type ReviewDecision = 'APPROVED' | 'REJECTED'

export interface Problem {
  type?: string
  title: string
  status: number
  detail?: string
  instance?: string
  traceId?: string
}

export interface ValidationProblem extends Problem {
  errors?: Array<{
    field?: string
    code?: string
    message?: string
  }>
}

export interface PageInfo {
  nextCursor: string | null
  hasMore: boolean
}

export interface HistoryEntry {
  actorId?: string
  action?: string
  at?: string
  payload?: Record<string, unknown>
}

export interface AttachmentRef {
  attachmentId: string
  alt?: string
  role?: 'INLINE' | 'ATTACHMENT' | 'COVER'
}

export interface TimeRange {
  start?: string | null
  due?: string | null
}

export interface LoginRequest {
  accountName: string
  password: string
}

export interface TokenPair {
  accessToken: string
  refreshToken: string
  expiresIn: number
  tokenType: string
}

export interface OrgSettings {
  orgMaxDepth?: number
  leafTypes?: string[]
  attachmentMaxBytes?: number
  extras?: Record<string, unknown>
}

export interface Organization {
  id: string
  rootOrgId: string
  parentId?: string | null
  type: string
  name: string
  code: string
  managerId?: string | null
  leaderIds?: string[]
  isLeaf?: boolean
  depth?: number
  path?: string
  timezone?: string
  locale?: string
  settings?: OrgSettings | null
  createdAt?: string
  updatedAt?: string
  deletedAt?: string | null
  schemaVersion?: number
}

export interface OrganizationPage {
  items: Organization[]
  pageInfo: PageInfo
}

export interface QaSettings {
  dualSignRequired?: boolean
  requiredReviewerRoles?: Role[]
}

export interface GroupSettings {
  qa: QaSettings
  extras?: Record<string, unknown>
}

export interface Group {
  id: string
  rootOrgId: string
  organizationId: string
  type: string
  name: string
  code: string
  attributes?: Record<string, unknown>
  leaderId?: string | null
  settings: GroupSettings
  createdAt?: string
  updatedAt?: string
  deletedAt?: string | null
  schemaVersion?: number
}

export interface GroupPage {
  items: Group[]
  pageInfo: PageInfo
}

export interface GroupMembership {
  id: string
  rootOrgId: string
  groupId: string
  userId: string
  role: GroupMembershipRole
  joinedAt: string
  leftAt?: string | null
  createdBy?: string
}

export interface User {
  id: string
  rootOrgId: string
  accountName: string
  employeeNo?: string
  email?: string
  displayName: string
  roles: Role[]
  groupIds?: string[]
  orgManagerScopes?: string[]
  primaryOrgPath?: string[]
  hrSyncedAt?: string
  active: boolean
  createdAt?: string
}

export interface UserPage {
  items: User[]
  pageInfo: PageInfo
}

export interface TemplateRef {
  templateType?: 'PROJECT_TEMPLATE' | 'TASK_TEMPLATE'
  templateScope?: TemplateScope
  templateId?: string
  templateVersion?: number
  instantiatedAt?: string
}

export interface Project {
  id: string
  rootOrgId: string
  organizationId: string
  name: string
  descriptionMarkdown?: string
  status: ProjectStatus
  ownerId: string
  memberIds: string[]
  groupIds: string[]
  schedule?: TimeRange
  tags?: string[]
  templateRef?: TemplateRef | null
  overdue?: boolean
  createdAt?: string
  updatedAt?: string
  deletedAt?: string | null
  schemaVersion?: number
}

export interface ProjectPage {
  items: Project[]
  pageInfo: PageInfo
}

export interface Membership {
  id?: string
  rootOrgId?: string
  projectId?: string
  userId: string
  role?: MembershipRole
  joinedAt?: string
}

export interface QaReviewPolicy {
  dualSignRequired: boolean
  requiredReviewerRoles: Role[]
  snapshotAt: string
  sourceGroupIds?: string[]
}

export interface QaReviewEntry {
  reviewerId: string
  reviewerRole: Role
  decision: ReviewDecision
  reason?: string
  at: string
}

export interface Task {
  id: string
  rootOrgId: string
  projectId: string
  type: string
  attributes?: Record<string, unknown>
  title: string
  descriptionMarkdown?: string
  status: TaskStatus
  priority: Priority
  ownerId: string
  assignees: string[]
  attachments?: AttachmentRef[]
  schedule?: TimeRange
  completedAt?: string | null
  originActionRequestId?: string | null
  templateRef?: TemplateRef | null
  qaReviewPolicy: QaReviewPolicy
  qaReviews?: QaReviewEntry[]
  tags?: string[]
  overdue?: boolean
  createdAt?: string
  updatedAt?: string
  deletedAt?: string | null
  schemaVersion?: number
}

export interface TaskPage {
  items: Task[]
  pageInfo: PageInfo
}

export interface TaskTypeDefinition {
  type: string
  label?: string
  description?: string
  attributesSchema?: Record<string, unknown>
  requiresReview?: boolean
}

export interface ActionRequest {
  id: string
  rootOrgId: string
  targetOrgId: string
  originatingOrgId?: string
  title: string
  descriptionMarkdown?: string
  status: ActionRequestStatus
  severity?: SeverityLevel
  ownerId: string
  attachments?: AttachmentRef[]
  dueAt?: string | null
  relatedTaskIds?: string[]
  createdBy?: string
  createdAt?: string
  updatedAt?: string
  deletedAt?: string | null
}

export interface ActionRequestPage {
  items: ActionRequest[]
  pageInfo: PageInfo
}

export interface CreateProjectRequest {
  organizationId: string
  name: string
  descriptionMarkdown?: string
  ownerId: string
  memberIds?: string[]
  groupIds: string[]
  schedule?: TimeRange
  tags?: string[]
  fromTemplateId?: string
  fromTemplateScope?: TemplateScope
  fromTemplateVersion?: number
}

export interface UpdateProjectRequest {
  name?: string
  descriptionMarkdown?: string
  schedule?: TimeRange
  groupIds?: string[]
  tags?: string[]
}

export interface CreateTaskRequest {
  projectId: string
  type: string
  attributes?: Record<string, unknown>
  title: string
  descriptionMarkdown?: string
  priority?: Priority
  ownerId: string
  assignees: string[]
  attachments?: AttachmentRef[]
  schedule?: TimeRange
  tags?: string[]
  fromTemplateId?: string
  fromTemplateScope?: TemplateScope
  fromTemplateVersion?: number
}

export interface UpdateTaskRequest {
  title?: string
  descriptionMarkdown?: string
  priority?: Priority
  attributes?: Record<string, unknown>
  schedule?: TimeRange
  tags?: string[]
  attachments?: AttachmentRef[]
}

export interface Attachment {
  id: string
  rootOrgId?: string
  filename: string
  contentType?: string
  sizeBytes?: number
  uploadUrl?: string
  downloadUrl?: string
  status?: 'PENDING' | 'UPLOADED' | 'FINALIZED' | 'DELETED'
  createdBy?: string
  createdAt?: string
}

export interface Comment {
  id: string
  taskId?: string
  authorId: string
  content: string
  createdAt: string
  updatedAt?: string
}

export interface ProjectTemplate {
  id: string
  scope: TemplateScope
  rootOrgId?: string | null
  name: string
  code: string
  descriptionMarkdown?: string
  tags?: string[]
  version?: number
  active?: boolean
  createdAt?: string
  updatedAt?: string
}

export interface ProjectTemplatePage {
  items: ProjectTemplate[]
  pageInfo: PageInfo
}

export interface TaskTemplate {
  id: string
  scope: TemplateScope
  rootOrgId?: string | null
  name: string
  code: string
  type?: string
  defaultPriority?: Priority
  tags?: string[]
  version?: number
  active?: boolean
  createdAt?: string
  updatedAt?: string
}

export interface TaskTemplatePage {
  items: TaskTemplate[]
  pageInfo: PageInfo
}

export interface Webhook {
  id: string
  url: string
  events?: string[]
  active?: boolean
  createdAt?: string
}

export interface CreateWebhookRequest {
  url: string
  events?: string[]
}

export interface NotificationPreference {
  channels?: Record<string, boolean>
}

export interface DispatchActionRequestRequest {
  title: string
  descriptionMarkdown?: string
  severity?: SeverityLevel
  attachments?: AttachmentRef[]
  dueAt?: string
  ownerId?: string
}
