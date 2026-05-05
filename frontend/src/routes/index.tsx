import { Routes, Route } from 'react-router-dom'
import { ProtectedRoute } from '@/auth/ProtectedRoute'
import { AppLayout } from '@/components/layout/AppShell'
import { LoginPage } from './LoginPage'
import { DailyBoardPage } from './DailyBoardPage'
import { ProjectsPage } from './ProjectsPage'
import { ProjectDetailPage } from './ProjectDetailPage'
import { TaskDetailPage } from './TaskDetailPage'
import { ActionRequestsPage } from './ActionRequestsPage'
import { ActionRequestDetailPage } from './ActionRequestDetailPage'
import { ActionRequestDispatchPage } from './ActionRequestDispatchPage'
import { OrganizationTreePage } from './OrganizationTreePage'
import { GroupListPage } from './GroupListPage'
import { GroupDetailPage } from './GroupDetailPage'
import { TemplatesPage } from './TemplatesPage'
import { ProfilePage } from './ProfilePage'
import { NotFoundPage } from './NotFoundPage'

function ProtectedLayout({ children }: { children: React.ReactNode }) {
  return (
    <ProtectedRoute>
      <AppLayout>{children}</AppLayout>
    </ProtectedRoute>
  )
}

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/"
        element={
          <ProtectedLayout>
            <DailyBoardPage />
          </ProtectedLayout>
        }
      />
      <Route
        path="/projects"
        element={
          <ProtectedLayout>
            <ProjectsPage />
          </ProtectedLayout>
        }
      />
      <Route
        path="/projects/:projectId"
        element={
          <ProtectedLayout>
            <ProjectDetailPage />
          </ProtectedLayout>
        }
      />
      <Route
        path="/tasks/:taskId"
        element={
          <ProtectedLayout>
            <TaskDetailPage />
          </ProtectedLayout>
        }
      />
      <Route
        path="/action-requests"
        element={
          <ProtectedLayout>
            <ActionRequestsPage />
          </ProtectedLayout>
        }
      />
      <Route
        path="/action-requests/:actionRequestId"
        element={
          <ProtectedLayout>
            <ActionRequestDetailPage />
          </ProtectedLayout>
        }
      />
      <Route
        path="/action-requests/dispatch"
        element={
          <ProtectedLayout>
            <ActionRequestDispatchPage />
          </ProtectedLayout>
        }
      />
      <Route
        path="/organizations"
        element={
          <ProtectedLayout>
            <OrganizationTreePage />
          </ProtectedLayout>
        }
      />
      <Route
        path="/groups"
        element={
          <ProtectedLayout>
            <GroupListPage />
          </ProtectedLayout>
        }
      />
      <Route
        path="/groups/:groupId"
        element={
          <ProtectedLayout>
            <GroupDetailPage />
          </ProtectedLayout>
        }
      />
      <Route
        path="/templates"
        element={
          <ProtectedLayout>
            <TemplatesPage />
          </ProtectedLayout>
        }
      />
      <Route
        path="/profile"
        element={
          <ProtectedLayout>
            <ProfilePage />
          </ProtectedLayout>
        }
      />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
