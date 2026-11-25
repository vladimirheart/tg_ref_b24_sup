'use strict';
// Minimal DOM text-node semantics used by the existing isolated audit/history tests.
function enableTextNodes(Element) {
  Object.defineProperty(Element.prototype, 'textContent', {
    configurable: true,
    get() { return (this._text || '') + (this.children || []).map(child => typeof child === 'string' ? child : child?.textContent || '').join(''); },
    set(value) { this._text = String(value ?? ''); this.children = []; },
  });
  Element.prototype.replaceChildren = function (...children) { this._text = ''; this.children = children; };
}
function identityContext(Element, document) {
  const dom = { ...document, createTextNode(value) { const node = new Element(); node.textContent = value; return node; } };
  return { window: {}, document: dom, employeeNode: username => dom.createTextNode(username || '—') };
}
module.exports = { enableTextNodes, identityContext };
