export function formatDate(value) {
  if (!value) {
    return '-';
  }

  const [year, month, day] = String(value).split('-');
  if (!year || !month || !day) {
    return value;
  }

  return `${day}/${month}/${year}`;
}

export function formatMoney(value) {
  return Number(value || 0).toLocaleString('vi-VN') + 'đ';
}
