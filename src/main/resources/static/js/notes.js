// Notes pages: background saving, the microphone recorder (phone or computer), live transcript,
// transcribe/summarize buttons and local times. Notes still save without this file; recording needs it.
(() => {
	'use strict';

	const storage = {
		get(key) {
			try { return localStorage.getItem(key); } catch { return null; }
		},
		set(key, value) {
			try { value == null ? localStorage.removeItem(key) : localStorage.setItem(key, value); } catch { /* private mode */ }
		},
		flash(value) {
			try { value == null ? sessionStorage.removeItem('notes.flash') : sessionStorage.setItem('notes.flash', value); } catch { /* ignore */ }
		},
		takeFlash() {
			try {
				const value = sessionStorage.getItem('notes.flash');
				sessionStorage.removeItem('notes.flash');
				return value;
			} catch { return null; }
		},
	};

	const formatClock = (totalSeconds) => {
		const s = Math.max(0, Math.floor(totalSeconds));
		const h = Math.floor(s / 3600);
		const m = Math.floor((s % 3600) / 60);
		const sec = String(s % 60).padStart(2, '0');
		return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${sec}` : `${m}:${sec}`;
	};

	// ----- Confirm before destructive buttons -----
	document.addEventListener('click', (event) => {
		const button = event.target.closest('[data-confirm]');
		if (button && !window.confirm(button.dataset.confirm)) {
			event.preventDefault();
			event.stopImmediatePropagation();
		}
	}, true);

	// ----- Show times in the phone's own time zone -----
	const now = new Date();
	document.querySelectorAll('time[data-local]').forEach((el) => {
		const date = new Date(el.getAttribute('datetime'));
		if (Number.isNaN(date.getTime())) return;
		const sameDay = date.toDateString() === now.toDateString();
		const time = date.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
		const day = date.toLocaleDateString(undefined, {
			month: 'short', day: 'numeric', year: date.getFullYear() === now.getFullYear() ? undefined : 'numeric',
		});
		el.textContent = sameDay ? `today ${time}` : (el.hasAttribute('data-with-time') ? `${day}, ${time}` : day);
	});

	// ----- Chrome saves recordings without a length, so the player can't seek until it has played through.
	// Jumping to the end once makes the browser work the length out.
	document.querySelectorAll('audio[data-fix-duration]').forEach((audio) => {
		audio.addEventListener('loadedmetadata', () => {
			if (audio.duration !== Infinity) return;
			audio.addEventListener('durationchange', () => { audio.currentTime = 0; }, { once: true });
			audio.currentTime = 1e101;
		}, { once: true });
	});

	// ----- Fetch helper: CSRF header, JSON in and out, readable errors -----
	const page = document.querySelector('[data-csrf]');
	const csrfHeaders = () => (page ? { [page.dataset.csrfHeader]: page.dataset.csrf } : {});

	const post = async (url, body, options = {}) => {
		const response = await fetch(url, {
			method: 'POST',
			body,
			headers: { 'X-Requested-With': 'fetch', Accept: 'application/json', ...csrfHeaders() },
			credentials: 'same-origin',
			...options,
		});
		const isJson = (response.headers.get('content-type') || '').includes('json');
		const data = isJson ? await response.json() : null;
		if (!response.ok) {
			throw new Error((data && data.error) || `Something went wrong (error ${response.status}).`);
		}
		// A login page instead of JSON means the session ran out.
		if (!data) throw new Error('You seem to be logged out. Log in again in another tab, then retry.');
		return data;
	};

	const reloadAt = (hash) => {
		history.replaceState(null, '', `${location.pathname}${location.search}${hash ? `#${hash}` : ''}`);
		location.reload();
	};

	// ----- "Record" on the notes list: name the new note with the local date and time -----
	document.querySelectorAll('[data-record-form]').forEach((form) => {
		form.addEventListener('submit', () => {
			const stamp = new Date().toLocaleString(undefined, {
				month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit',
			});
			form.querySelector('input[name="title"]').value = `Recording · ${stamp}`;
		});
	});

	// ----- Note editor: autosave -----
	const noteForm = document.querySelector('[data-note-form]');
	let flushNote = async () => {};
	let noteDirty = false;

	if (noteForm) {
		const status = noteForm.querySelector('[data-save-status]');
		const saveButton = noteForm.querySelector('[data-save-button]');
		const body = noteForm.querySelector('textarea[name="body"]');
		let timer = null;
		let saving = null;
		saveButton.hidden = true;

		const grow = () => {
			body.style.height = 'auto';
			body.style.height = `${Math.max(body.scrollHeight + 2, 192)}px`;
		};
		grow();
		body.addEventListener('input', grow);

		const save = async () => {
			clearTimeout(timer);
			if (!noteDirty) return saving;
			noteDirty = false;
			status.textContent = 'Saving…';
			saving = post(noteForm.action, new FormData(noteForm))
				.then(() => {
					status.textContent = noteDirty ? 'Saving…' : 'Saved';
					saveButton.hidden = true;
				})
				.catch((error) => {
					noteDirty = true;
					status.textContent = `Not saved: ${error.message}`;
					saveButton.hidden = false;
				});
			return saving;
		};
		flushNote = save;

		const schedule = (delay) => {
			noteDirty = true;
			status.textContent = 'Editing…';
			clearTimeout(timer);
			timer = setTimeout(save, delay);
		};
		noteForm.addEventListener('input', (event) => schedule(event.target.tagName === 'SELECT' ? 0 : 900));
		noteForm.addEventListener('change', (event) => { if (event.target.tagName === 'SELECT') schedule(0); });
		noteForm.addEventListener('submit', (event) => {
			event.preventDefault();
			noteDirty = true;
			save();
		});
		// Phones kill background tabs without warning, so save the moment the page is hidden.
		document.addEventListener('visibilitychange', () => {
			if (document.visibilityState === 'hidden' && noteDirty) {
				noteDirty = false;
				fetch(noteForm.action, {
					method: 'POST', body: new FormData(noteForm), keepalive: true, credentials: 'same-origin',
					headers: { 'X-Requested-With': 'fetch', ...csrfHeaders() },
				}).catch(() => { noteDirty = true; });
			}
		});
	}

	// ----- Transcripts: save when you leave the box -----
	document.querySelectorAll('[data-transcript-form]').forEach((form) => {
		const textarea = form.querySelector('textarea');
		const status = form.querySelector('[data-transcript-status]');
		const button = form.querySelector('[data-transcript-save]');
		let saved = textarea.value;
		button.hidden = true;
		const save = async () => {
			if (textarea.value === saved) return;
			const value = textarea.value;
			status.textContent = 'saving…';
			try {
				await post(form.action, new FormData(form));
				saved = value;
				status.textContent = 'edited · saved';
			} catch (error) {
				status.textContent = `not saved: ${error.message}`;
				button.hidden = false;
			}
		};
		textarea.addEventListener('input', () => { status.textContent = 'editing…'; });
		textarea.addEventListener('blur', save);
		form.addEventListener('submit', (event) => { event.preventDefault(); save(); });
	});

	// ----- Buttons that call the AI (transcribe, summarize): show progress, then reload -----
	const busyForm = (selector, busyText, hash) => {
		document.querySelectorAll(selector).forEach((form) => {
			form.addEventListener('submit', async (event) => {
				event.preventDefault();
				const button = form.querySelector('button');
				const label = button.textContent;
				button.disabled = true;
				button.innerHTML = `<span class="spinner" aria-hidden="true"></span> ${busyText}`;
				try {
					await flushNote();
					await post(form.action, new FormData(form));
					reloadAt(hash(form));
				} catch (error) {
					button.disabled = false;
					button.textContent = label;
					window.alert(error.message);
				}
			});
		});
	};
	busyForm('[data-summary-form]', 'Summarizing…', () => 'summary');
	busyForm('[data-transcribe-form]', 'Transcribing…', (form) => form.closest('article').id);

	// ----- Recorder -----
	const recorderEl = document.querySelector('[data-recorder]');
	if (!recorderEl) return;

	const $ = (name) => recorderEl.querySelector(`[data-${name}]`);
	const message = $('recorder-message');
	const say = (text, tone = 'muted') => {
		message.textContent = text;
		message.className = `text-sm ${tone === 'error' ? 'text-red-300' : tone === 'ok' ? 'text-accent' : 'text-muted'}`;
	};

	const flash = storage.takeFlash();
	if (flash) say(flash, 'error');

	const canRecord = Boolean(navigator.mediaDevices && navigator.mediaDevices.getUserMedia && window.MediaRecorder);
	$('recorder-unsupported').hidden = canRecord;
	$('recorder-controls').hidden = !canRecord;

	const recordButton = $('record');
	const pauseButton = $('pause');
	const stopButton = $('stop');
	const timerEl = $('timer');
	const stateEl = $('recorder-state');
	const levelEl = $('level');
	const liveOutput = $('live-output');
	const liveFinalEl = $('live-final');
	const liveInterimEl = $('live-interim');

	if (new URLSearchParams(location.search).has('record') && canRecord) {
		recorderEl.scrollIntoView({ block: 'center' });
		recordButton.focus({ preventScroll: true });
		stateEl.textContent = 'Tap the red button to start';
	}

	// Live transcript uses the browser's own speech recognition (Chrome, Edge, Safari). It's free but rough;
	// when the server can transcribe, that runs after upload and is usually better.
	const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
	const serverTranscribes = recorderEl.dataset.serverTranscribe === 'true';
	const liveToggle = $('live-toggle');
	const liveCheckbox = $('live-checkbox');
	if (SpeechRecognition) {
		liveToggle.hidden = false;
		const saved = storage.get('notes.liveTranscript');
		liveCheckbox.checked = saved === null ? !serverTranscribes : saved === 'true';
		liveCheckbox.addEventListener('change', () => storage.set('notes.liveTranscript', String(liveCheckbox.checked)));
	}

	let state = 'idle'; // idle | recording | paused | saving | failed
	let stream = null;
	let recorder = null;
	let chunks = [];
	let elapsedBefore = 0;
	let segmentStart = 0;
	let tick = null;
	let audioContext = null;
	let meterFrame = null;
	let wakeLock = null;
	let recognition = null;
	let liveText = '';
	let pendingUpload = null;

	const elapsedSeconds = () => (elapsedBefore + (state === 'recording' ? Date.now() - segmentStart : 0)) / 1000;

	const pickMimeType = () => {
		const options = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4', 'audio/ogg;codecs=opus', 'audio/ogg'];
		return options.find((type) => MediaRecorder.isTypeSupported && MediaRecorder.isTypeSupported(type)) || '';
	};

	const render = () => {
		const active = state === 'recording' || state === 'paused';
		recordButton.dataset.state = state;
		recordButton.disabled = state === 'saving' || active;
		recordButton.setAttribute('aria-label', active ? 'Recording' : 'Start recording');
		pauseButton.hidden = !active;
		pauseButton.textContent = state === 'paused' ? 'Resume' : 'Pause';
		stopButton.hidden = !active;
		stateEl.textContent = {
			idle: 'Tap to record', recording: '● Recording', paused: 'Paused', saving: 'Saving…', failed: 'Not saved',
		}[state];
		stateEl.classList.toggle('text-red-400', state === 'recording');
		if (liveCheckbox) liveCheckbox.disabled = active;
		timerEl.textContent = formatClock(elapsedSeconds());
	};

	// Input level bar, so you can see the mic is actually hearing you.
	const startMeter = () => {
		if (!audioContext) return;
		const source = audioContext.createMediaStreamSource(stream);
		const analyser = audioContext.createAnalyser();
		analyser.fftSize = 512;
		source.connect(analyser);
		const samples = new Uint8Array(analyser.fftSize);
		const draw = () => {
			analyser.getByteTimeDomainData(samples);
			let sum = 0;
			for (const sample of samples) sum += ((sample - 128) / 128) ** 2;
			const rms = Math.sqrt(sum / samples.length);
			levelEl.style.width = state === 'recording' ? `${Math.min(100, rms * 400)}%` : '0%';
			meterFrame = requestAnimationFrame(draw);
		};
		draw();
	};

	// Keep the phone screen on while recording; a locked screen can pause the mic in mobile browsers.
	const holdWakeLock = async () => {
		try {
			if ('wakeLock' in navigator) wakeLock = await navigator.wakeLock.request('screen');
		} catch { /* not allowed, fine */ }
	};
	document.addEventListener('visibilitychange', () => {
		if (document.visibilityState === 'visible' && (state === 'recording' || state === 'paused')) holdWakeLock();
	});

	const renderLive = (interim = '') => {
		liveFinalEl.textContent = liveText;
		liveInterimEl.textContent = interim;
		liveOutput.hidden = !liveText && !interim;
	};

	const startRecognition = () => {
		if (!SpeechRecognition || !liveCheckbox.checked) return;
		recognition = new SpeechRecognition();
		// Android Chrome repeats earlier words in continuous mode, so there it listens phrase by phrase and restarts.
		recognition.continuous = !/Android/i.test(navigator.userAgent);
		recognition.interimResults = true;
		recognition.lang = navigator.language || 'en-US';
		recognition.onresult = (event) => {
			let interim = '';
			for (let i = event.resultIndex; i < event.results.length; i++) {
				const result = event.results[i];
				if (result.isFinal) liveText = `${liveText} ${result[0].transcript.trim()}`.trim();
				else interim += result[0].transcript;
			}
			renderLive(interim);
		};
		recognition.onerror = (event) => {
			if (event.error === 'not-allowed' || event.error === 'service-not-allowed' || event.error === 'audio-capture') {
				recognition = null;
				say('Live transcript isn\'t available in this browser, but the audio is still recording.');
			}
		};
		recognition.onend = () => {
			// It stops by itself after a pause in speech; keep it going for the whole recording.
			if (recognition && state === 'recording') {
				setTimeout(() => { try { recognition && recognition.start(); } catch { /* already started */ } }, 200);
			}
		};
		try { recognition.start(); } catch { recognition = null; }
	};

	const stopRecognition = () => {
		const current = recognition;
		recognition = null;
		if (current) { try { current.stop(); } catch { /* ignore */ } }
	};

	const releaseMic = () => {
		if (stream) stream.getTracks().forEach((track) => track.stop());
		stream = null;
		if (meterFrame) cancelAnimationFrame(meterFrame);
		meterFrame = null;
		levelEl.style.width = '0%';
		if (audioContext) audioContext.close().catch(() => {});
		audioContext = null;
		if (wakeLock) wakeLock.release().catch(() => {});
		wakeLock = null;
		clearInterval(tick);
	};

	const start = async () => {
		if (state !== 'idle' && state !== 'failed') return;
		// Created inside the tap so iPhones allow it.
		try { audioContext = new (window.AudioContext || window.webkitAudioContext)(); } catch { audioContext = null; }
		try {
			stream = await navigator.mediaDevices.getUserMedia({
				audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
			});
		} catch (error) {
			releaseMic();
			if (error && error.name === 'NotAllowedError') {
				say('Microphone access is blocked. Allow the microphone for this site in your browser settings '
					+ '(iPhone: Settings → Apps → Safari → Microphone; Android: tap the lock icon by the address), then try again.', 'error');
			} else if (error && error.name === 'NotFoundError') {
				say('No microphone was found on this device.', 'error');
			} else {
				say(`Couldn't start the microphone: ${error && error.message ? error.message : error}`, 'error');
			}
			return;
		}

		const mimeType = pickMimeType();
		try {
			recorder = new MediaRecorder(stream, { ...(mimeType ? { mimeType } : {}), audioBitsPerSecond: 48000 });
		} catch {
			recorder = new MediaRecorder(stream);
		}
		chunks = [];
		recorder.addEventListener('dataavailable', (event) => { if (event.data && event.data.size) chunks.push(event.data); });
		recorder.addEventListener('stop', finish);
		recorder.start(1000);

		if (pendingUpload) { pendingUpload = null; $('retry-box') && $('retry-box').remove(); }
		liveText = '';
		renderLive();
		elapsedBefore = 0;
		segmentStart = Date.now();
		state = 'recording';
		if (audioContext && audioContext.state === 'suspended') audioContext.resume().catch(() => {});
		startMeter();
		holdWakeLock();
		startRecognition();
		tick = setInterval(render, 250);
		say('');
		render();
	};

	const togglePause = () => {
		if (state === 'recording') {
			recorder.pause();
			elapsedBefore += Date.now() - segmentStart;
			state = 'paused';
			stopRecognition();
		} else if (state === 'paused') {
			recorder.resume();
			segmentStart = Date.now();
			state = 'recording';
			startRecognition();
		}
		render();
	};

	const stop = () => {
		if (state !== 'recording' && state !== 'paused') return;
		if (state === 'recording') elapsedBefore += Date.now() - segmentStart;
		state = 'saving';
		stopRecognition();
		render();
		recorder.stop(); // fires 'stop' → finish()
	};

	const extensionFor = (type) => (type.includes('mp4') ? 'm4a' : type.includes('ogg') ? 'ogg' : 'webm');

	async function finish() {
		releaseMic();
		const type = (recorder && recorder.mimeType) || chunks[0]?.type || 'audio/webm';
		const blob = new Blob(chunks, { type });
		chunks = [];
		pendingUpload = { blob, seconds: elapsedBefore / 1000, transcript: liveText };
		await upload();
	}

	const upload = async () => {
		if (!pendingUpload) return;
		const { blob, seconds, transcript } = pendingUpload;
		state = 'saving';
		render();
		say(serverTranscribes ? 'Uploading and transcribing… this can take a minute for long recordings.' : 'Uploading…');
		const form = new FormData();
		form.append('audio', blob, `recording.${extensionFor(blob.type)}`);
		form.append('duration', String(Math.round(seconds)));
		if (transcript) form.append('transcript', transcript);
		try {
			await flushNote();
			const data = await post(recorderEl.dataset.uploadUrl, form);
			pendingUpload = null;
			if (data.warning) storage.flash(data.warning);
			state = 'idle';
			reloadAt('recordings');
		} catch (error) {
			state = 'failed';
			render();
			say(`The recording wasn't saved: ${error.message}`, 'error');
			showRetry(blob);
		}
	};

	// If the upload fails (bad signal, logged out) the audio stays in the page so nothing is lost.
	const showRetry = (blob) => {
		let box = $('retry-box');
		if (!box) {
			box = document.createElement('div');
			box.dataset.retryBox = '';
			box.className = 'flex flex-wrap gap-2';
			message.after(box);
		}
		box.innerHTML = '';
		const retry = document.createElement('button');
		retry.type = 'button';
		retry.className = 'btn';
		retry.textContent = 'Retry upload';
		retry.addEventListener('click', upload);
		const download = document.createElement('a');
		download.className = 'btn-ghost';
		download.href = URL.createObjectURL(blob);
		download.download = `recording-${new Date().toISOString().slice(0, 16).replace(/[:T]/g, '-')}.${extensionFor(blob.type)}`;
		download.textContent = 'Download to keep it';
		box.append(retry, download);
	};

	recordButton.addEventListener('click', start);
	pauseButton.addEventListener('click', togglePause);
	stopButton.addEventListener('click', stop);
	render();

	window.addEventListener('beforeunload', (event) => {
		if (state === 'recording' || state === 'paused' || state === 'saving' || pendingUpload || noteDirty) {
			event.preventDefault();
			event.returnValue = '';
		}
	});

	// ----- Upload an existing audio file -----
	const uploadForm = document.querySelector('[data-upload-form]');
	if (uploadForm) {
		const durationOf = (file) => new Promise((resolve) => {
			const audio = document.createElement('audio');
			const url = URL.createObjectURL(file);
			const done = (value) => { URL.revokeObjectURL(url); resolve(Number.isFinite(value) ? value : 0); };
			audio.preload = 'metadata';
			audio.onloadedmetadata = () => done(audio.duration);
			audio.onerror = () => done(0);
			setTimeout(() => done(0), 4000);
			audio.src = url;
		});
		uploadForm.addEventListener('submit', async (event) => {
			event.preventDefault();
			const file = uploadForm.querySelector('input[type="file"]').files[0];
			if (!file) return;
			const button = uploadForm.querySelector('button');
			button.disabled = true;
			button.innerHTML = '<span class="spinner" aria-hidden="true"></span> Uploading…';
			try {
				const form = new FormData(uploadForm);
				form.set('duration', String(Math.round(await durationOf(file))));
				await flushNote();
				const data = await post(uploadForm.action, form);
				if (data.warning) storage.flash(data.warning);
				reloadAt('recordings');
			} catch (error) {
				button.disabled = false;
				button.textContent = 'Upload';
				say(error.message, 'error');
			}
		});
	}
})();
