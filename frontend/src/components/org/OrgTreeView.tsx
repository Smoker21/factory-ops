import { Stack, Text, Badge, Group, Collapse, ActionIcon } from '@mantine/core'
import { useState } from 'react'
import type { OrgTreeNode } from '@/hooks/useOrgTree'

interface OrgTreeViewProps {
  nodes: OrgTreeNode[]
  onSelect?: (node: OrgTreeNode) => void
  selectedId?: string
}

const TYPE_COLORS: Record<string, string> = {
  FAB: 'blue',
  DIVISION: 'cyan',
  DEPARTMENT: 'teal',
  SECTION: 'green',
}

function OrgNode({
  node,
  onSelect,
  selectedId,
  level,
}: {
  node: OrgTreeNode
  onSelect?: (n: OrgTreeNode) => void
  selectedId?: string
  level: number
}) {
  const [expanded, setExpanded] = useState(level < 2)
  const hasChildren = node.children.length > 0

  return (
    <Stack gap={2}>
      <Group
        gap="xs"
        style={{
          paddingLeft: level * 16,
          paddingTop: 4,
          paddingBottom: 4,
          paddingRight: 8,
          borderRadius: 8,
          cursor: 'pointer',
          background: selectedId === node.id ? 'var(--mantine-color-blue-light)' : undefined,
          minHeight: 44,
        }}
        onClick={() => onSelect?.(node)}
      >
        {hasChildren && (
          <ActionIcon
            size="xs"
            variant="transparent"
            onClick={(e) => {
              e.stopPropagation()
              setExpanded((v) => !v)
            }}
            style={{ minHeight: 24, minWidth: 24 }}
          >
            {expanded ? '▼' : '▶'}
          </ActionIcon>
        )}
        {!hasChildren && <span style={{ width: 24 }} />}
        <Badge size="xs" color={TYPE_COLORS[node.type] ?? 'gray'}>
          {node.type}
        </Badge>
        <Text size="sm">{node.name}</Text>
        {node.isLeaf && (
          <Badge size="xs" color="green" variant="outline">
            leaf
          </Badge>
        )}
      </Group>
      {hasChildren && (
        <Collapse in={expanded}>
          {node.children.map((child) => (
            <OrgNode
              key={child.id}
              node={child}
              onSelect={onSelect}
              selectedId={selectedId}
              level={level + 1}
            />
          ))}
        </Collapse>
      )}
    </Stack>
  )
}

export function OrgTreeView({ nodes, onSelect, selectedId }: OrgTreeViewProps) {
  if (nodes.length === 0) {
    return <Text c="dimmed">沒有組織資料</Text>
  }
  return (
    <Stack gap={2}>
      {nodes.map((node) => (
        <OrgNode
          key={node.id}
          node={node}
          onSelect={onSelect}
          selectedId={selectedId}
          level={0}
        />
      ))}
    </Stack>
  )
}
