/**
 * Letter avatar generator for email sender display.
 *
 * Generates deterministic two-letter initials and color classes
 * for email senders when avatar images are unavailable.
 *
 * Used as a privacy-conscious fallback before external avatar services.
 */

/**
 * Color palette for letter avatars.
 * Selected for accessibility (WCAG AA contrast with white text) and visual variety.
 */
const AVATAR_COLORS = [
  'bg-blue-500',
  'bg-purple-500',
  'bg-green-500',
  'bg-orange-500',
  'bg-pink-500',
  'bg-indigo-500',
  'bg-teal-500',
  'bg-red-500',
  'bg-cyan-500',
  'bg-amber-500'
];

/**
 * Simple string hash function for deterministic color selection.
 * Same input always produces the same hash.
 * @param {string} str - String to hash
 * @returns {number} Hash value
 */
function hashString(str: string) {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = str.charCodeAt(i) + ((hash << 5) - hash);
    hash = hash & hash; // Convert to 32-bit integer
  }
  return Math.abs(hash);
}

/**
 * Extracts two initials from a name or email.
 * @param {string} name - Sender name (e.g., "John Doe")
 * @param {string} email - Sender email (fallback if name empty, e.g., "john@example.com")
 * @returns {string} Two uppercase initials
 * @example
 * extractInitials('John Doe', '') // => 'JD'
 * extractInitials('Alice', '') // => 'AL'
 * extractInitials('', 'bob@example.com') // => 'BO'
 */
function extractInitials(name: string | null | undefined, email: string | null | undefined) {
  const source = (name && name.trim()) || (email && email.trim()) || '??';

  // Remove email domain if using email as source
  const cleanSource = source.includes('@') ? source.split('@')[0] : source;

  // Split on whitespace, punctuation, or camelCase
  const parts = cleanSource
    .replace(/[._-]/g, ' ')
    .split(/\s+/)
    .filter(part => part.length > 0);

  if (parts.length >= 2) {
    // Two or more parts: take first char of first two parts
    return (parts[0][0] + parts[1][0]).toUpperCase();
  } else if (parts.length === 1 && parts[0].length >= 2) {
    // Single part with 2+ chars: take first two chars
    return parts[0].substring(0, 2).toUpperCase();
  } else if (parts.length === 1 && parts[0].length === 1) {
    // Single char: duplicate it
    return (parts[0][0] + parts[0][0]).toUpperCase();
  }

  // Fallback for edge cases
  return '??';
}

/**
 * Generates letter avatar data (2 initials + Tailwind color class) from sender info.
 * @param {string} name - Sender name (e.g., "John Doe")
 * @param {string} email - Sender email (fallback if name empty, e.g., "john@example.com")
 * @returns {{ initials: string, colorClass: string }} - Two uppercase initials and bg-* color class
 * @example
 * getLetterAvatarData('John Doe', 'john@example.com')
 * // => { initials: 'JD', colorClass: 'bg-blue-500' }
 *
 * getLetterAvatarData('Alice', 'alice@example.com')
 * // => { initials: 'AL', colorClass: 'bg-purple-500' }
 *
 * getLetterAvatarData('', 'bob@example.com')
 * // => { initials: 'BO', colorClass: 'bg-green-500' }
 */
export function getLetterAvatarData(name: string | null | undefined, email: string | null | undefined) {
  const initials = extractInitials(name, email);

  // Use name or email for color selection (prefer name for consistency)
  const colorSource = (name && name.trim()) || (email && email.trim()) || 'default';
  const colorIndex = hashString(colorSource) % AVATAR_COLORS.length;
  const colorClass = AVATAR_COLORS[colorIndex];

  return { initials, colorClass };
}
