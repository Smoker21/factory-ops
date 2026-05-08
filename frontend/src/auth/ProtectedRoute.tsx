import { Navigate, useLocation } from 'react-router-dom'
import { Loader, Center } from '@mantine/core'
import { useAuth } from './useAuth'

interface ProtectedRouteProps {
  children: React.ReactNode
}

export function ProtectedRoute({ children }: ProtectedRouteProps) {
  const { isAuthenticated, isLoading } = useAuth()
  const location = useLocation()

  // While the /me bootstrap call is in-flight we cannot know whether the user
  // is authenticated.  Rendering a loader avoids a flash-redirect to /login on
  // valid sessions that are simply waiting for the cookie-based /me response.
  if (isLoading) {
    return (
      <Center mih="100vh">
        <Loader size="lg" />
      </Center>
    )
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  return <>{children}</>
}
