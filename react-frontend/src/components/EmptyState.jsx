function EmptyState({ colSpan, message }) {
  return (
    <tr>
      <td className="empty-state" colSpan={colSpan}>
        {message}
      </td>
    </tr>
  );
}

export default EmptyState;
