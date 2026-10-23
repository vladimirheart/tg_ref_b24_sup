const test = require('node:test');
const assert = require('node:assert/strict');
const content = require('../../main/resources/static/js/knowledge-note-content.js');
const { hourlySeries } = require('../../main/resources/static/js/home-pulse.js');
test('Заметки ищутся по полному тексту и названию, без учёта регистра и ё/е', () => {
  assert(content.matches({ title: 'Роутер', body: 'А'.repeat(1000) + 'съёмка' }, 'СЪЕМКА'));
  assert(content.matches({ title: 'РОУТЕР' }, 'роутер'));
  assert(!content.matches({ title: 'Роутер' }, 'принтер'));
});
test('Фото разрешены только по защищённому локальному raster URL', () => {
  const path = '/api/attachments/knowledge-base/abc_%D1%84%D0%BE%D1%82%D0%BE.png';
  assert.equal(content.photoPath(path), path);
  for (const path of ['https://example.com/a.png', '//example.com/a.png', '/api/attachments/knowledge-base/a.svg',
    '/api/attachments/knowledge-base/../a.png', '/api/attachments/knowledge-base/%2e%2e%2fa.png',
    '/api/attachments/knowledge-base/a.png?token=x', '/api/attachments/knowledge-base/%00a.png']) assert.equal(content.photoPath(path), null);
});
test('Текст сохраняется, фото распознаются, произвольный HTML не интерпретируется', () => {
  const body = 'До\n![Фото](/api/attachments/knowledge-base/test.png)\nПосле <script>alert(1)</script>';
  assert.deepEqual(content.parts(body), [{ text: 'До\n' }, { photo: '/api/attachments/knowledge-base/test.png', alt: 'Фото' }, { text: '\nПосле <script>alert(1)</script>' }]);
  assert.deepEqual(content.parts('![X](https://example.com/a.png)'), [{ text: '![X](https://example.com/a.png)' }]);
});
test('Нагрузка содержит ровно 24 реальных часа, отсутствующие данные не демонстрационные', () => {
  assert.deepEqual(hourlySeries([]), Array(24).fill(0));
  assert.equal(hourlySeries([2, 3])[1], 3);
  assert.equal(hourlySeries([-1])[0], 0);
});
