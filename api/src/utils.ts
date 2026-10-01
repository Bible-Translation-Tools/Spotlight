export default function validateEmoji(input: string): boolean {
  const segmenter = new Intl.Segmenter("en", { granularity: "grapheme" });
  const segments = [...segmenter.segment(input)];
  if (segments.length !== 1) return false;
  const emoji = segments[0].segment;
  return /\p{Extended_Pictographic}|\p{Emoji_Presentation}/u.test(emoji);
}
