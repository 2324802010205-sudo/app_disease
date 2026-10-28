export function downloadCsv(filename: string, headers: string[], rows: unknown[][]) {
  const escape = (value: unknown) => `"${String(value ?? '').replaceAll('"', '""')}"`
  const csv = '\uFEFF' + [headers, ...rows].map(row => row.map(escape).join(',')).join('\r\n')
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url; link.download = filename; document.body.appendChild(link); link.click(); link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
export async function collectPages<T>(fetch: (cursor?: string | null) => Promise<{items: T[]; nextCursor: string | null}>): Promise<T[]> {
  const result: T[] = []
  let cursor: string | null = null
  do {
    const page = await fetch(cursor)
    result.push(...page.items)
    cursor = page.nextCursor
  } while (cursor)
  return result
}
export function dateTime(value?: number | null) { return value ? new Date(value).toLocaleString('vi-VN') : '—' }
