import { useQuery } from '@tanstack/react-query'
import { listOrganizations, getOrganization } from '@/api/organizations'
import type { Organization } from '@/api/types'
import { useAuth } from '@/auth/useAuth'

export interface OrgTreeNode extends Organization {
  children: OrgTreeNode[]
}

function buildTree(items: Organization[]): OrgTreeNode[] {
  const map = new Map<string, OrgTreeNode>()
  const roots: OrgTreeNode[] = []

  items.forEach((item) => {
    map.set(item.id, { ...item, children: [] })
  })

  items.forEach((item) => {
    const node = map.get(item.id)!
    if (item.parentId && map.has(item.parentId)) {
      map.get(item.parentId)!.children.push(node)
    } else {
      roots.push(node)
    }
  })

  return roots
}

export function useOrgTree() {
  const { user } = useAuth()
  const rootOrgId = user?.rootOrgId

  return useQuery({
    queryKey: ['org-tree', rootOrgId],
    queryFn: async () => {
      const page = await listOrganizations({ limit: 100 })
      return buildTree(page.items)
    },
    enabled: !!rootOrgId,
    staleTime: 1000 * 60 * 5,
  })
}

export function useOrganization(orgId: string) {
  return useQuery({
    queryKey: ['organizations', orgId],
    queryFn: () => getOrganization(orgId),
    enabled: !!orgId,
  })
}
