import { useQuery } from '@tanstack/react-query'
import { listTasks } from '@/api/tasks'
import type { Task } from '@/api/types'
import { useAuth } from '@/auth/useAuth'
import dayjs from 'dayjs'

export interface DailyBoardData {
  myOwnedTasks: Task[]
  myAssignedTasks: Task[]
  groupOverdueTasks: Task[]
  pendingReviewTasks: Task[]
}

export function useDailyBoard() {
  const { user } = useAuth()
  const userId = user?.id

  const myOwnedQuery = useQuery({
    queryKey: ['daily-board', 'owned', userId],
    queryFn: () =>
      listTasks({
        ownerId: userId,
        limit: 20,
      }),
    enabled: !!userId,
  })

  const myAssignedQuery = useQuery({
    queryKey: ['daily-board', 'assigned', userId],
    queryFn: () =>
      listTasks({
        assigneeId: userId,
        limit: 20,
      }),
    enabled: !!userId,
  })

  const groupIds = user?.groupIds ?? []

  const groupOverdueQuery = useQuery({
    queryKey: ['daily-board', 'group-overdue', groupIds],
    queryFn: async () => {
      const results = await Promise.all(
        groupIds.slice(0, 3).map((groupId) =>
          listTasks({
            groupId,
            dueBefore: dayjs().toISOString(),
            limit: 10,
          })
        )
      )
      const allTasks = results.flatMap((r) => r.items)
      const uniqueTasks = Array.from(new Map(allTasks.map((t) => [t.id, t])).values())
      return uniqueTasks.filter(
        (t) => t.status !== 'DONE' && t.status !== 'CANCELLED' && t.overdue
      )
    },
    enabled: groupIds.length > 0,
  })

  const pendingReviewQuery = useQuery({
    queryKey: ['daily-board', 'pending-review', userId],
    queryFn: () =>
      listTasks({
        status: 'IN_REVIEW',
        limit: 20,
      }),
    enabled: !!userId,
  })

  const isLoading =
    myOwnedQuery.isLoading ||
    myAssignedQuery.isLoading ||
    groupOverdueQuery.isLoading ||
    pendingReviewQuery.isLoading

  const ownedTasks = myOwnedQuery.data?.items ?? []
  const assignedTasks = (myAssignedQuery.data?.items ?? []).filter(
    (t) => t.ownerId !== userId
  )

  const pendingReview = (pendingReviewQuery.data?.items ?? []).filter((task) => {
    if (!task.qaReviewPolicy?.dualSignRequired) return false
    if (!user) return false
    const requiredRoles = task.qaReviewPolicy.requiredReviewerRoles ?? []
    const userRoles = user.roles ?? []
    const hasReviewRole = requiredRoles.some((r) => userRoles.includes(r))
    if (!hasReviewRole) return false
    const alreadyReviewed = (task.qaReviews ?? []).some(
      (review) =>
        review.reviewerId === userId && requiredRoles.includes(review.reviewerRole)
    )
    return !alreadyReviewed
  })

  return {
    isLoading,
    data: {
      myOwnedTasks: ownedTasks,
      myAssignedTasks: assignedTasks,
      groupOverdueTasks: groupOverdueQuery.data ?? [],
      pendingReviewTasks: pendingReview,
    },
  }
}
