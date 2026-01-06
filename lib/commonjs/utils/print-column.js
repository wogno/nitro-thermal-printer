"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.processColumnText = void 0;
/**
 * Using to add space for each row
 * @param text
 * @param restLength
 * @param align
 */
const processAlignText = (text, restLength, align) => {
  if (align === 0) {
    return text + " ".repeat(restLength);
  } else if (align === 1) {
    return " ".repeat(Math.floor(restLength / 2)) + text + " ".repeat(Math.ceil(restLength / 2));
  } else if (align === 2) {
    return " ".repeat(restLength) + text;
  }
  return "";
};

/**
 * process down line when length of text is bigger than columnWidthAtRow
 * @param text
 * @param maxLength
 */
const processNewLine = (text, maxLength) => {
  let newText;
  let newTextTail;
  const next_char = text.slice(maxLength, maxLength + 1);
  if (next_char === " ") {
    newText = text.slice(0, maxLength);
    newTextTail = text.slice(maxLength, text.length);
  } else {
    const newMaxLength = text.slice(0, maxLength).split("").map(e => e).lastIndexOf(" ");
    if (newMaxLength === -1) {
      newText = text.slice(0, maxLength);
      newTextTail = text.slice(maxLength, text.length);
    } else {
      newText = text.slice(0, newMaxLength);
      newTextTail = text.slice(newMaxLength, text.length);
    }
  }
  return {
    text: newText,
    text_tail: newTextTail.trim()
  };
};
const processColumnText = (texts, columnWidth, columnAlignment, columnStyle = []) => {
  const rest_texts = new Array(texts.length).fill("");
  let result = "";
  const lastIndex = texts.length - 1;
  texts.forEach((text, idx) => {
    const columnWidthAtRow = Math.round(columnWidth?.[idx] ?? 10);
    if (text.length >= columnWidthAtRow) {
      const processedText = processNewLine(text, columnWidthAtRow);
      result += (columnStyle?.[idx] ?? "") + processAlignText(processedText.text, columnWidthAtRow - processedText.text.length, columnAlignment[idx] ?? 0) + (idx !== lastIndex ? " " : "");
      rest_texts[idx] = processedText.text_tail;
    } else {
      result += (columnStyle?.[idx] ?? "") + processAlignText(text.trim(), columnWidthAtRow - text.length, columnAlignment[idx] ?? 0) + (idx !== lastIndex ? " " : "");
    }
  });
  const index_nonEmpty = rest_texts.findIndex(rest_text => rest_text !== "");
  if (index_nonEmpty !== -1) {
    result += "\n" + processColumnText(rest_texts, columnWidth, columnAlignment, columnStyle);
  }
  return result;
};
exports.processColumnText = processColumnText;
//# sourceMappingURL=print-column.js.map