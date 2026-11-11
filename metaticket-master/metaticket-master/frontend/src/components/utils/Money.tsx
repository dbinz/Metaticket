const Money = ({ amount, currency }: { amount: number; currency?: string }) => {
  const formatter = new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: currency ?? 'VND',
  })

  return <>{formatter.format(amount)}</>
}

export { Money }
