const $ = id => document.getElementById(id);
let selected = null;
let searchVersion = 0;
let answerVersion = 0;
let selectionVersion = 0;
let indexBusy = false;
let questionReady = false;
async function request(url, options) {
  const response = await fetch(url, options);
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.message || '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.');
  return data;
}
function status(id, message, error = false) {
  $(id).textContent = message;
  $(id).classList.toggle('error', error);
}
function select(doc) {
  selected = doc;
  searchVersion++;
  answerVersion++;
  const selection = ++selectionVersion;
  questionReady = false;
  $('question').disabled = $('ask-button').disabled = true;
  $('index-controls').hidden = true;
  status('index-status', '');
  loadIndexStatus(doc, selection);
  $('answer').replaceChildren();
  status('answer-status', '');
  $('selected-title').textContent = doc.originalFilename;
  $('document-meta').textContent = `${doc.pageCount}페이지 · ${doc.chunkCount}개 구간 · ${(doc.size / 1024).toFixed(0)}KB`;
  $('query').disabled = $('search-button').disabled = false;
  $('results').replaceChildren();
  status('search-status', '');
  document.querySelectorAll('.document').forEach(el => {
    el.classList.toggle('active', el.dataset.id === doc.id);
    el.setAttribute('aria-pressed', String(el.dataset.id === doc.id));
  });
}
async function refresh(preferredId) {
  const documents = await request('/api/documents');
  $('count').textContent = documents.length;
  $('documents').replaceChildren();
  for (const doc of documents) {
    const button = document.createElement('button');
    button.className = 'document'; button.dataset.id = doc.id;
    const title = document.createElement('strong'); title.textContent = doc.originalFilename;
    const meta = document.createElement('small'); meta.textContent = `${doc.pageCount}페이지 · 저장 완료`;
    button.append(title, meta); button.onclick = () => select(doc);
    $('documents').append(button);
  }
  const doc = documents.find(d => d.id === (preferredId || selected?.id)) || documents[0];
  if (doc) select(doc);
}
$('upload-form').addEventListener('submit', async event => {
  event.preventDefault();
  const file = $('file').files[0];
  if (!file) return;
  if (file.size > 20 * 1024 * 1024) return status('upload-status', '20MB 이하의 PDF를 선택해 주세요.', true);
  $('upload-button').disabled = true;
  status('upload-status', '문서를 읽고 저장하는 중이에요…');
  try {
    const body = new FormData(); body.append('file', file);
    const doc = await request('/api/documents/upload', { method: 'POST', body });
    await refresh(doc.id);
    status('upload-status', `${doc.pageCount}페이지를 저장했어요.`);
    $('file').value = '';
  } catch (error) { status('upload-status', error.message, true); }
  finally { $('upload-button').disabled = false; }
});
$('search-form').addEventListener('submit', async event => {
  event.preventDefault();
  if (!selected) return;
  const doc = selected;
  const version = ++searchVersion;
  $('search-button').disabled = true;
  $('results').replaceChildren();
  status('search-status', '문서에서 찾고 있어요…');
  try {
    const matches = await request(`/api/documents/${doc.id}/search?q=${encodeURIComponent($('query').value)}`);
    if (version !== searchVersion) return;
    status('search-status', matches.length ? `관련 구간 ${matches.length}개를 찾았어요.` : '일치하는 내용이 없어요. 문서에 있는 다른 키워드로 검색해 보세요.');
    for (const match of matches) {
      const article = document.createElement('article'); article.className = 'match';
      const link = document.createElement('a');
      link.href = `/api/documents/${doc.id}/pdf#page=${match.pageNumber}`;
      link.target = '_blank'; link.rel = 'noopener'; link.textContent = `${match.pageNumber}페이지 원문 보기 ↗`;
      const p = document.createElement('p'); p.textContent = match.text;
      article.append(link, p); $('results').append(article);
    }
  } catch (error) { if (version === searchVersion) status('search-status', error.message, true); }
  finally { if (version === searchVersion) $('search-button').disabled = false; }
});
refresh().catch(error => status('upload-status', error.message, true));

$('ask-form').addEventListener('submit', async event => {
  event.preventDefault();
  if (!selected) return;
  const doc = selected;
  const version = ++answerVersion;
  $('ask-button').disabled = true;
  $('answer').replaceChildren();
  status('answer-status', '근거를 찾고 답변을 준비하고 있어요…');
  try {
    const data = await request(`/api/documents/${doc.id}/ask`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ question: $('question').value })
    });
    if (version !== answerVersion) return;
    const labels = { GENERATED: 'AI 생성 답변 · 원문을 확인해 주세요', EXTRACTIVE: '원문 검색 결과 · AI 생성 아님', NO_EVIDENCE: '답변 보류' };
    status('answer-status', labels[data.mode] || '답변');
    const p = document.createElement('p'); p.textContent = data.answer;
    $('answer').append(p);
    for (const source of data.sources) {
      const article = document.createElement('article'); article.className = 'match';
      const link = document.createElement('a'); link.href = source.url;
      link.target = '_blank'; link.rel = 'noopener';
      link.textContent = `[${source.sourceId}] ${source.pageNumber}페이지 원문 보기 ↗`;
      const excerpt = document.createElement('p'); excerpt.textContent = source.text;
      article.append(link, excerpt); $('answer').append(article);
    }
  } catch (error) { if (version === answerVersion) status('answer-status', error.message, true); }
  finally { if (version === answerVersion) $('ask-button').disabled = !questionReady; }
});

function showIndexState(data) {
  $('index-controls').hidden = !data.enabled;
  questionReady = !data.enabled || data.ready;
  $('question').disabled = $('ask-button').disabled = !questionReady;
  $('index-button').disabled = indexBusy;
  $('index-button').textContent = data.ready ? '의미 검색 다시 준비' : '의미 검색 준비';
  $('retrieval-notice').textContent = data.enabled
    ? '질문은 문장의 의미로 근거를 찾습니다. 위 검색창은 기존 키워드 검색입니다. 유사도는 정답 확률이 아니므로 출처를 확인하세요.'
    : '질문은 키워드 검색으로 근거를 찾습니다. AI 답변은 출처와 함께 확인하세요.';
  status('index-status', data.ready ? `의미 검색 준비 완료 · ${data.indexedChunks}개 구간`
    : '처음 한 번 의미 검색 준비를 눌러 주세요. PDF를 다시 업로드할 필요는 없습니다.');
}
async function loadIndexStatus(doc, selection) {
  try {
    const data = await request(`/api/documents/${doc.id}/index`);
    if (selection !== selectionVersion) return;
    showIndexState(data);
  } catch (error) {
    if (selection === selectionVersion) status('answer-status', error.message, true);
  }
}
$('index-button').addEventListener('click', async () => {
  if (!selected || indexBusy) return;
  const doc = selected;
  const selection = selectionVersion;
  answerVersion++;
  indexBusy = true;
  questionReady = false;
  $('question').disabled = $('ask-button').disabled = $('index-button').disabled = true;
  status('index-status', '문서의 의미를 분석하고 있어요. 처음에는 모델을 불러오는 시간이 필요합니다…');
  try {
    const data = await request(`/api/documents/${doc.id}/index`, { method: 'POST' });
    if (selection === selectionVersion) showIndexState(data);
  } catch (error) {
    if (selection === selectionVersion) {
      // A failed rebuild preserves the previous complete index.
      await loadIndexStatus(doc, selection);
      if (selection === selectionVersion) status('index-status', error.message, true);
    }
  } finally {
    indexBusy = false;
    $('index-button').disabled = false;
  }
});
