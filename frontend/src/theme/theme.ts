import { createTheme, type MantineThemeOverride } from '@mantine/core'

// Factory-floor UX: large touch targets (>=44px), high contrast, mobile-first
export const theme: MantineThemeOverride = createTheme({
  fontFamily: 'Noto Sans TC, Microsoft JhengHei, sans-serif',
  fontSizes: {
    xs: '0.8rem',
    sm: '0.9rem',
    md: '1rem',
    lg: '1.125rem',
    xl: '1.25rem',
  },
  spacing: {
    xs: '0.5rem',
    sm: '0.75rem',
    md: '1rem',
    lg: '1.5rem',
    xl: '2rem',
  },
  components: {
    Button: {
      defaultProps: {
        size: 'md',
      },
      styles: {
        root: {
          minHeight: '44px',
          minWidth: '44px',
          fontWeight: 600,
        },
      },
    },
    ActionIcon: {
      defaultProps: {
        size: 'lg',
      },
      styles: {
        root: {
          minHeight: '44px',
          minWidth: '44px',
        },
      },
    },
    TextInput: {
      defaultProps: {
        size: 'md',
      },
    },
    Select: {
      defaultProps: {
        size: 'md',
      },
    },
    Textarea: {
      defaultProps: {
        size: 'md',
      },
    },
    NavLink: {
      styles: {
        root: {
          minHeight: '44px',
        },
      },
    },
  },
  primaryColor: 'blue',
  defaultRadius: 'md',
})
