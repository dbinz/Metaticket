/**
 * List all components and their variants for quick theme check
 *
 */
'use client'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  FormField,
  FormItem,
  FormLabel,
  FormControl,
  FormMessage,
} from '@/components/ui/form'
import { Select, SelectContent, SelectItem } from '@/components/ui/select'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import { useState } from 'react'

const colors = [
  { label: 'default', fg: 'text-foreground', bg: 'bg-background' },
  {
    label: 'default (variant)',
    fg: 'text-default-foreground',
    bg: 'bg-default',
  },
  { label: 'primary', fg: 'text-primary-foreground', bg: 'bg-primary' },
  { label: 'secondary', fg: 'text-secondary-foreground', bg: 'bg-secondary' },
  { label: 'tertiary', fg: 'text-tertiary-foreground', bg: 'bg-tertiary' },
  { label: 'accent', fg: 'text-accent-foreground', bg: 'bg-accent' },
  {
    label: 'destructive',
    fg: 'text-destructive-foreground',
    bg: 'bg-destructive',
  },
  { label: 'muted', fg: 'text-muted-foreground', bg: 'bg-muted' },
  { label: 'popover', fg: 'text-popover-foreground', bg: 'bg-popover' },
  { label: 'card', fg: 'text-card-foreground', bg: 'bg-card' },
]

const brands = [
  { label: 'orange', bg: 'bg-brand-orange' },
  { label: 'blue', bg: 'bg-brand-blue' },
  { label: 'red', bg: 'bg-brand-red' },
]

const invertedColors = [
  { label: 'default', fg: 'text-background', bg: 'bg-foreground' },
  {
    label: 'destructive',
    fg: 'text-destructive',
    bg: 'bg-destructive-foreground',
  },
  { label: 'primary', fg: 'text-primary', bg: 'bg-primary-foreground' },
]

type ButtonVariant =
  | 'default'
  | 'primary'
  | 'secondary'
  | 'tertiary'
  | 'accent'
  | 'destructive'
  | 'ghost'
  | 'link'
  | 'outline'
const buttonVariants: ButtonVariant[] = [
  'default',
  'primary',
  'secondary',
  'tertiary',
  'accent',
  'destructive',
  'ghost',
  'link',
  'outline',
]

export default function Theme() {
  const [isDark, setIsDark] = useState<boolean>(false)

  const toggleTheme = () => {
    setIsDark((isDark) => !isDark)
  }

  return (
    <div className={isDark ? 'dark' : ''}>
      <div className="p-5 text-foreground bg-background">
        <h1 className="text-4xl font-bold">Design system</h1>
        (Almost) All components and their variants for quick check
        <Button onClick={toggleTheme} className="block mb-5" size="sm">
          Toggle theme
        </Button>
        <div className="mb-5">
          <h3 className="text-2xl font-bold mb-5">Colors</h3>
          <h4 className="text-xl mt-4">Brand colors</h4>
          <div className="grid grid-cols-4 gap-4">
            {brands.map((color) => (
              <div
                key={color.label}
                className={`p-4 ${color.bg} text-white border-2`}
              >
                <div className="text-lg font-bold">{color.label}</div>
                <div className="text-sm">{color.bg}</div>
              </div>
            ))}
          </div>

          <h4 className="text-xl mt-4">Normal</h4>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
            {colors.map((color) => (
              <div
                key={color.label}
                className={`p-4 ${color.bg} ${color.fg} border-2`}
              >
                <div className="text-lg font-bold">{color.label}</div>
                <div className="text-sm">
                  {color.fg} <br />
                  {color.bg}
                </div>
              </div>
            ))}
          </div>

          <h4 className="text-xl mt-4">Inverted</h4>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
            {invertedColors.map((color) => (
              <div
                key={color.label}
                className={`p-4 ${color.bg} ${color.fg} border-2`}
              >
                <div className="text-lg font-bold">inverted {color.label}</div>
                <div className="text-sm">
                  {color.fg} <br />
                  {color.bg}
                </div>
              </div>
            ))}
          </div>
        </div>
        <div className="mb-5">
          <h3 className="text-2xl font-bold mb-5">Buttons</h3>
          <div className="flex gap-4">
            {buttonVariants.map((variant) => (
              <div key={variant} className="mb-5">
                <Button variant={variant}>{variant}</Button>
              </div>
            ))}
          </div>
        </div>
        <div className="mb-5">
          <h3 className="text-2xl font-bold mb-5">Inputs</h3>
          <div className="grid sm:grid-cols-2 gap-4">
            <Input placeholder="Input" />
          </div>
        </div>
      </div>
    </div>
  )
}
