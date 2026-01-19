export function parseUssdCode(fullCode: string): { code: string; messages: string[] } {
  const stripped = fullCode.substring(1, fullCode.length - 1);
  const items = stripped.split('*');
  const code = `*${items[0]}#`;
  const messages = items.slice(1);
  return { code, messages };
}
