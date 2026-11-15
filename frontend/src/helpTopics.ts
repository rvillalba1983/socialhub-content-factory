/**
 * Catálogo de «¿Dónde lo saco?»: de dónde sale cada dato externo que SocialHub te pide. Es la única fuente de los
 * enlaces: la ayuda de la interfaz (`WhereHelp`), `MANUAL.md` y `docs/TUTORIAL.md` repiten los mismos (hay una
 * prueba que lo comprueba). Solo enlaces oficiales; los de Meta Business piden iniciar sesión y por eso no se
 * pueden abrir sin ella (ver `verified`). Aquí nunca va ningún valor real: ni claves ni identificadores.
 */
export type HelpLink = { label: string; url: string };
export type HelpStep = string | { text: string; link: HelpLink };

export type HelpTopic = {
  /** Dato tal como lo llama la interfaz. */
  label: string;
  /** Para qué lo usa SocialHub. */
  purpose: string;
  /** Dónde se saca, en una línea (para las tablas del manual y del tutorial). */
  where: string;
  steps: HelpStep[];
  /** Enlaces de apoyo que no encajan en un paso concreto. */
  links?: HelpLink[];
  /** Aviso corto (por ejemplo, que es un secreto). */
  warning?: string;
};

const DRIVE_DOWNLOAD: HelpLink = { label: 'Descargar Google Drive para escritorio', url: 'https://www.google.com/drive/download/' };
const DRIVE_WEB: HelpLink = { label: 'Google Drive en la web', url: 'https://drive.google.com' };
const DRIVE_SHARE: HelpLink = { label: 'Ayuda de Google: compartir archivos y carpetas', url: 'https://support.google.com/drive/answer/7166529' };
const META_BUSINESS: HelpLink = { label: 'Configuración de Meta Business', url: 'https://business.facebook.com/settings' };
const META_SYSTEM_USERS: HelpLink = { label: 'Usuarios del sistema (Meta Business)', url: 'https://business.facebook.com/settings/system-users' };
const META_SYSTEM_USERS_HELP: HelpLink = { label: 'Ayuda de Meta: añadir usuarios del sistema', url: 'https://www.facebook.com/business/help/503306463479099' };
const META_APPS: HelpLink = { label: 'Tus apps de Meta para desarrolladores', url: 'https://developers.facebook.com/apps' };
const META_PUBLISHING: HelpLink = { label: 'Meta: publicar contenido con la API de Instagram', url: 'https://developers.facebook.com/documentation/instagram-platform/content-publishing' };
const META_LOGIN_FLOW: HelpLink = {
  label: 'Meta: crear el inicio de sesión a mano (OAuth)',
  url: 'https://developers.facebook.com/docs/facebook-login/guides/advanced/manual-flow',
};
const META_IG_LOGIN: HelpLink = {
  label: 'Meta: API de Instagram con inicio de sesión de Facebook',
  url: 'https://developers.facebook.com/docs/instagram-platform/instagram-api-with-facebook-login',
};
const INSTAGRAM_WEB: HelpLink = { label: 'Instagram en la web', url: 'https://www.instagram.com/' };
const PUBLER_APP: HelpLink = { label: 'Publer (iniciar sesión)', url: 'https://app.publer.com' };
const PUBLER_PRO: HelpLink = {
  label: 'Ayuda de Publer: pasar Instagram a cuenta profesional',
  url: 'https://publer.com/help/en/article/how-to-switch-my-instagram-account-to-a-creatorbusiness-account-wc99en',
};
const PUBLER_TIKTOK: HelpLink = { label: 'Ayuda de Publer: conectar TikTok', url: 'https://publer.com/help/en/article/how-to-connect-tiktok-accounts-4wlb4o/' };
const PUBLORA_APP: HelpLink = { label: 'Publora (iniciar sesión)', url: 'https://publora.com' };
const PUBLORA_DOCS: HelpLink = { label: 'Documentación de Publora: autenticación', url: 'https://docs.publora.com/authentication' };
const TIKTOK_PROFILE: HelpLink = { label: 'Perfil de TikTok @litoques.eldelito', url: 'https://www.tiktok.com/@litoques.eldelito' };
const CLAUDE_SCHEDULED: HelpLink = {
  label: 'Ayuda de Claude: tareas programadas',
  url: 'https://support.claude.com/en/articles/13854387-schedule-recurring-tasks-in-claude-cowork',
};
const CLAUDE_DOWNLOAD: HelpLink = { label: 'Descargar la app de Claude', url: 'https://claude.ai/download' };

const COPY_PATH =
  'Copia la ruta: en el Explorador de archivos, con la carpeta o el archivo seleccionado, pulsa Ctrl + Mayús + C (o clic derecho › Copiar como ruta de acceso). En Mac: Opción + clic derecho › Copiar como nombre de ruta. Quita las comillas del principio y del final.';

const TOPICS = {
  'meta-app': {
    label: 'App ID y App Secret de la app de Meta',
    where: 'Meta for Developers › tu app › Configuración de la app › Básica («Identificador de la app» y «Clave secreta de la app»).',
    purpose: 'Para «Conectar con Instagram»: SocialHub los usa para pedir permiso a Meta en tu nombre y detectar la cuenta de cada marca. Se pegan una sola vez.',
    steps: [
      { text: 'Abre tus apps de Meta for Developers e inicia sesión.', link: META_APPS },
      'Entra en la app de SocialHub. En modo desarrollo vale: tú eres administrador y no hace falta la revisión de Meta.',
      'En el menú de la izquierda, pulsa Configuración de la app › Básica.',
      'Copia el «Identificador de la app» (App ID) y pégalo en el primer campo de arriba.',
      'En «Clave secreta de la app» (App Secret) pulsa «Mostrar» (te pide tu contraseña), cópiala y pégala en el segundo campo.',
      'Pulsa «Comprobar y guardar»: SocialHub los comprueba con Meta antes de guardarlos.',
    ],
    links: [META_IG_LOGIN],
    warning: 'El App Secret es un secreto: no lo pegues en chats ni lo guardes en Drive. SocialHub lo guarda solo en este ordenador y no vuelve a enseñarlo.',
  },
  'meta-redirect-uri': {
    label: 'Dirección de redireccionamiento OAuth (para Meta)',
    where: 'Meta for Developers › tu app › Inicio de sesión con Facebook › Configuración › «URI de redireccionamiento OAuth válidas».',
    purpose: 'Meta solo devuelve a SocialHub a una dirección que tú hayas autorizado. Es la que sale en el campo de arriba: cópiala tal cual en tu app.',
    steps: [
      'Pulsa «Copiar» en el campo de arriba (es http://localhost:8686/api/instagram/oauth/callback).',
      { text: 'Abre tus apps de Meta for Developers y entra en la de SocialHub.', link: META_APPS },
      'En el menú de la izquierda, pulsa Inicio de sesión con Facebook › Configuración.',
      'Pega la dirección en «URI de redireccionamiento OAuth válidas» y pulsa «Guardar cambios».',
      'Comprueba que «Inicio de sesión de OAuth de cliente» y «Inicio de sesión de OAuth web» están activados.',
    ],
    links: [META_LOGIN_FLOW],
    warning: 'Tiene que ser idéntica, letra por letra (http, localhost y el puerto 8686). Si no, Meta responde que la dirección no coincide.',
  },
  'meta-token': {
    label: 'Clave de la API de Instagram (token de Meta)',
    where: 'Meta Business › Configuración › Usuarios › Usuarios del sistema › «Generar identificador» (caducidad «Nunca»; permisos instagram_basic e instagram_content_publish).',
    purpose: 'Alternativa a «Conectar con Instagram»: la clave de un «usuario del sistema» no caduca y vale para todas las marcas. Se usa cuando no hay una conexión vigente.',
    steps: [
      { text: 'Abre «Usuarios del sistema» de tu porfolio empresarial e inicia sesión.', link: META_SYSTEM_USERS },
      'Elige el usuario del sistema de SocialHub (rol Administrador). Si no existe, pulsa «Añadir».',
      'En «Asignar activos», comprueba que tiene la app de SocialHub (control total) y las cuentas de Instagram de tus marcas.',
      'Pulsa «Generar identificador», elige la app, caducidad «Nunca» y marca los permisos instagram_basic e instagram_content_publish.',
      'Copia el identificador generado (Meta lo enseña una sola vez) y pégalo en el campo de arriba.',
    ],
    links: [META_SYSTEM_USERS_HELP, META_APPS, META_PUBLISHING],
    warning: 'Es un secreto: no lo pegues en chats ni lo guardes en Drive. Si pasó por un chat, genera otro y borra el anterior.',
  },
  'instagram-account-id': {
    label: 'Identificador de la cuenta de Instagram (API oficial)',
    where: 'Meta Business › Configuración › Cuentas › Cuentas de Instagram › la cuenta de la marca › su ID (número largo).',
    purpose: 'Decirle a Meta en qué cuenta publica esta marca. «Conectar con Instagram» lo rellena solo si el @usuario coincide; si no, elige la cuenta de la lista o pega aquí su número.',
    steps: [
      { text: 'Abre la configuración de tu porfolio en Meta Business e inicia sesión.', link: META_BUSINESS },
      'Entra en Cuentas › Cuentas de Instagram.',
      'Pulsa la cuenta de esta marca (por ejemplo, @elde_lito o @litoques).',
      'Copia el ID de la cuenta: es un número largo, solo con cifras.',
      'Pégalo en el campo de arriba. SocialHub lo comprueba con Meta y enseña el @usuario; si no es el de la marca, es otra cuenta.',
    ],
    links: [{ label: PUBLER_PRO.label, url: PUBLER_PRO.url }],
    warning: 'La cuenta tiene que ser profesional (Empresa o Creador) y estar asignada al usuario del sistema.',
  },
  'brand-handle': {
    label: 'Cuenta de la marca',
    where: 'Tu perfil de Instagram: el @usuario que sale arriba.',
    purpose: 'El nombre de usuario de la marca en la red. SocialHub lo muestra y lo usa para reconocer la marca.',
    steps: [
      { text: 'Abre Instagram e inicia sesión con la cuenta de la marca; abre su perfil.', link: INSTAGRAM_WEB },
      'Copia el nombre de usuario que sale arriba, con la @ (por ejemplo, @elde_lito).',
    ],
  },
  'publer-account-instagram': {
    label: 'Cuenta de Instagram en Publer',
    where: 'Publer › lista de cuentas sociales: el nombre de la cuenta de Instagram, tal cual.',
    purpose: 'Claude programa en Publer con Chrome y busca la cuenta por este nombre. Sin ella, la publicación queda retenida.',
    steps: [
      { text: 'Abre Publer e inicia sesión con tu cuenta.', link: PUBLER_APP },
      'Mira el nombre con el que sale la cuenta de Instagram de la marca en la lista de cuentas sociales de Publer.',
      'Cópialo tal cual (mayúsculas, tildes y símbolos incluidos) y pégalo en el campo de arriba.',
      'Si la cuenta no está conectada, conéctala primero en Publer (el plan Free admite 3 cuentas).',
    ],
    links: [PUBLER_PRO],
    warning: 'La cuenta de Instagram tiene que ser profesional (Empresa o Creador).',
  },
  'publer-account-tiktok': {
    label: 'Cuenta de TikTok en Publer',
    where: 'Publer › lista de cuentas sociales: el nombre de la cuenta de TikTok (@litoques.eldelito sale como «Lito · Litoques · El DeLito»).',
    purpose: 'Claude programa los TikTok en Publer con Chrome y busca la cuenta por este nombre. Las dos marcas pueden usar la misma.',
    steps: [
      { text: 'Conecta la cuenta de TikTok (@litoques.eldelito, personal y pública) en Publer si aún no lo está.', link: PUBLER_TIKTOK },
      { text: 'Abre Publer e inicia sesión.', link: PUBLER_APP },
      'Mira el nombre con el que sale la cuenta de TikTok en la lista de cuentas sociales (en este caso, «Lito · Litoques · El DeLito»).',
      'Cópialo tal cual y pégalo en el campo de arriba.',
    ],
    links: [TIKTOK_PROFILE],
  },
  'publora-key': {
    label: 'Clave de la API de Publora',
    where: 'Publora › «API» en la barra lateral › «Generate API Key» (se ve una sola vez).',
    purpose: 'Publicar por Publora cuando falla Publer. Vale para todas las marcas y se guarda solo en este ordenador.',
    steps: [
      { text: 'Entra en Publora e inicia sesión.', link: PUBLORA_APP },
      'Pulsa «API» en la barra lateral.',
      'Pulsa «Generate API Key».',
      'Copia la clave enseguida: se ve una sola vez. Pégala en el campo de arriba.',
    ],
    links: [PUBLORA_DOCS],
    warning: 'Es un secreto: no la pegues en chats ni la guardes en Drive.',
  },
  'publora-account': {
    label: 'Cuenta de Publora de la marca',
    where: 'Publora › «Channels» › «Add Channel»; después se elige en SocialHub de la lista de cuentas conectadas.',
    purpose: 'Qué cuenta conectada en Publora usa esta marca en cada red. La lista sale de tu cuenta de Publora, una vez puesta la clave.',
    steps: [
      'Pon primero la clave de Publora (arriba).',
      { text: 'Si tu cuenta no sale en la lista, conéctala en Publora: «Channels» en la barra lateral › «Add Channel» y sigue la autorización.', link: PUBLORA_APP },
      'Vuelve aquí, elige la cuenta de cada red y pulsa «Guardar cuentas».',
    ],
    links: [PUBLORA_DOCS],
  },
  'drive-root': {
    label: 'Carpeta raíz de Drive de la marca',
    where: 'Explorador de archivos › unidad G: (Google Drive para escritorio) › «Mi unidad» › la carpeta de la marca › copiar la ruta.',
    purpose: 'La carpeta donde SocialHub crea borradores, generados, aprobados, publicados, informes y encargos. Tiene que estar en el disco que sincroniza Google Drive.',
    steps: [
      { text: 'Instala Google Drive para escritorio e inicia sesión: en el Explorador de archivos aparece la unidad G: con «Mi unidad».', link: DRIVE_DOWNLOAD },
      'Dentro de «Mi unidad», crea (o elige) la carpeta de la marca; por ejemplo, G:\\Mi unidad\\socialhub\\eldelito.',
      COPY_PATH,
      'Pégala en el campo de arriba y pulsa «Crear carpetas».',
    ],
    links: [DRIVE_WEB],
  },
  'drive-role-paths': {
    label: 'Ruta de cada carpeta de Drive',
    where: 'Explorador de archivos › la carpeta de ese papel dentro de G: › copiar la ruta.',
    purpose: 'Qué carpeta hace cada papel (material, borradores, encargos…). Con «Crear carpetas» se rellenan solas; cámbialas solo si quieres otra ruta.',
    steps: [
      { text: 'Comprueba que Google Drive para escritorio está abierto (unidad G:).', link: DRIVE_DOWNLOAD },
      'Abre en el Explorador de archivos la carpeta que quieres para ese papel.',
      COPY_PATH,
      'Pégala en el campo de ese papel y pulsa «Guardar carpetas».',
    ],
  },
  'drive-public': {
    label: 'Carpeta «publico» compartida',
    where: 'drive.google.com › Mi unidad › la marca › encargos › publico › Compartir › «Cualquier persona con el enlace» (Lector).',
    purpose: 'Meta descarga las imágenes de Instagram de un enlace público. SocialHub deja una copia en «publico» y la borra al terminar. SocialHub no puede comprobar este permiso.',
    steps: [
      { text: 'Abre Google Drive en la web e inicia sesión.', link: DRIVE_WEB },
      'Ve a Mi unidad › la carpeta de la marca › encargos › publico.',
      'Clic derecho en «publico» › Compartir.',
      'En «Acceso general», elige «Cualquier persona con el enlace» con el permiso «Lector».',
      'Pulsa «Listo». Después, en «Te falta por completar», pulsa «Ya está compartida».',
    ],
    links: [DRIVE_SHARE],
  },
  'backups-folder': {
    label: 'Carpeta de copias de seguridad',
    where: 'Explorador de archivos › una carpeta de Drive en G: › copiar la ruta.',
    purpose: 'Dónde se guardan las copias de SocialHub. Si es una carpeta de Drive, la copia queda fuera de este ordenador.',
    steps: [
      { text: 'Comprueba que Google Drive para escritorio está abierto (unidad G:).', link: DRIVE_DOWNLOAD },
      'En «Mi unidad» crea (o elige) la carpeta de copias; por ejemplo, G:\\Mi unidad\\SocialHub\\Copias.',
      COPY_PATH,
      'Pégala en el campo de arriba y pulsa «Guardar carpeta».',
    ],
  },
  'local-path': {
    label: 'Ruta de un archivo o carpeta del ordenador',
    where: 'Explorador de archivos › seleccionar el archivo o la carpeta › Ctrl + Mayús + C.',
    purpose: 'SocialHub necesita la ruta completa de un archivo (un spec.json, una pieza, un documento) o de una carpeta de Drive.',
    steps: [
      { text: 'Comprueba que Google Drive para escritorio está abierto (unidad G:).', link: DRIVE_DOWNLOAD },
      'Busca el archivo o la carpeta en el Explorador de archivos.',
      COPY_PATH,
      'Pégala en el campo de arriba.',
    ],
  },
  'claude-tasks': {
    label: 'Tareas programadas de Claude',
    where: 'App de Claude › «Scheduled» en la barra izquierda.',
    purpose: 'Claude hace los encargos de SocialHub desde dos tareas programadas: «Encargos de SocialHub» (nube) y «Publicar con Chrome (SocialHub)» (tu PC). SocialHub no llama a Claude.',
    steps: [
      { text: 'Abre la app de Claude e inicia sesión con tu cuenta de pago.', link: CLAUDE_DOWNLOAD },
      'En la barra de la izquierda pulsa «Scheduled»: ahí salen tus tareas programadas y sus ejecuciones.',
      'Comprueba que están las dos. Si falta alguna, pídele a Claude que la cree con el texto de docs/tarea-programada-encargos.md (la de Chrome, desde una conversación vinculada a tu PC).',
    ],
    links: [CLAUDE_SCHEDULED],
  },
} satisfies Record<string, HelpTopic>;

export type HelpTopicId = keyof typeof TOPICS;
export const HELP_TOPICS: Record<HelpTopicId, HelpTopic> = TOPICS;

/** Todos los enlaces del catálogo, sin repetir. */
export function allHelpLinks(): HelpLink[] {
  const seen = new Map<string, HelpLink>();
  for (const t of Object.values(HELP_TOPICS) as HelpTopic[]) {
    for (const s of t.steps) if (typeof s !== 'string') seen.set(s.link.url, s.link);
    for (const l of t.links ?? []) seen.set(l.url, l);
  }
  return [...seen.values()];
}
