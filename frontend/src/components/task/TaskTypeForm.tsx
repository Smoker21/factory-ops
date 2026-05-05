import { TextInput, Select, Stack, Text } from '@mantine/core'
import { useFormContext } from 'react-hook-form'

interface TaskTypeFormProps {
  taskType: string
}

// Type-specific attribute fields
const TYPE_FIELDS: Record<string, Array<{ key: string; label: string; type: string }>> = {
  EQUIPMENT_INSPECTION: [
    { key: 'equipmentId', label: '設備編號', type: 'text' },
    { key: 'checklistVersion', label: '點檢表版本', type: 'text' },
    { key: 'location', label: '設備位置', type: 'text' },
  ],
  INCIDENT_RESPONSE: [
    { key: 'incidentType', label: '異常類型', type: 'text' },
    { key: 'equipmentId', label: '設備編號', type: 'text' },
    { key: 'severity', label: '嚴重程度', type: 'select' },
  ],
  SHIFT_HANDOVER: [
    { key: 'shiftFrom', label: '交班班次', type: 'text' },
    { key: 'shiftTo', label: '接班班次', type: 'text' },
    { key: 'handoverNotes', label: '交班備注', type: 'text' },
  ],
  GENERAL: [],
}

const SEVERITY_OPTIONS = [
  { value: 'LOW', label: '低' },
  { value: 'MEDIUM', label: '中' },
  { value: 'HIGH', label: '高' },
  { value: 'CRITICAL', label: '緊急' },
]

export function TaskTypeForm({ taskType }: TaskTypeFormProps) {
  const { register, setValue, watch } = useFormContext()
  const fields = TYPE_FIELDS[taskType] ?? []

  if (fields.length === 0) return null

  return (
    <Stack gap="sm">
      <Text size="sm" fw={600}>
        型態特有屬性
      </Text>
      {fields.map((field) => {
        if (field.type === 'select') {
          return (
            <Select
              key={field.key}
              label={field.label}
              data={SEVERITY_OPTIONS}
              value={watch(`attributes.${field.key}`) ?? ''}
              onChange={(v) => setValue(`attributes.${field.key}`, v)}
            />
          )
        }
        return (
          <TextInput
            key={field.key}
            label={field.label}
            {...register(`attributes.${field.key}`)}
          />
        )
      })}
    </Stack>
  )
}
