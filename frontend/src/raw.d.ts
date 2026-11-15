// Archivos leídos como texto con «?raw» (los usan las pruebas)
declare module '*?raw' {
  const text: string;
  export default text;
}
