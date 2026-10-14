// Workout pages: background saving of sets, progress, rest timer, live clock, search and the chart tooltip.
// Every page still works without this file; it just reloads more.
(() => {
	'use strict';

	const storage = {
		get(key) {
			try { return localStorage.getItem(key); } catch { return null; }
		},
		set(key, value) {
			try { value == null ? localStorage.removeItem(key) : localStorage.setItem(key, value); } catch { /* private mode */ }
		},
	};

	const formatClock = (totalSeconds) => {
		const s = Math.max(0, Math.round(totalSeconds));
		const h = Math.floor(s / 3600);
		const m = Math.floor((s % 3600) / 60);
		const sec = String(s % 60).padStart(2, '0');
		return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${sec}` : `${m}:${sec}`;
	};

	// Keep sticky bars just under the site header, whose height changes when the nav wraps on phones.
	const header = document.querySelector('body > header');
	if (header) {
		const setHeaderHeight = () => document.documentElement.style.setProperty('--header-h', `${header.offsetHeight}px`);
		setHeaderHeight();
		window.addEventListener('resize', setHeaderHeight);
	}

	// ----- Confirm before destructive buttons -----
	document.addEventListener('click', (event) => {
		const button = event.target.closest('[data-confirm]');
		if (button && !window.confirm(button.dataset.confirm)) {
			event.preventDefault();
		}
	});

	// ----- Live workout clock -----
	const clock = document.querySelector('[data-elapsed]');
	if (clock) {
		const startedAt = Date.now() - Number(clock.dataset.elapsed) * 1000;
		const tick = () => { clock.textContent = formatClock((Date.now() - startedAt) / 1000); };
		tick();
		setInterval(tick, 1000);
	}

	// ----- Rest timer -----
	const rest = document.querySelector('[data-rest]');
	const restLength = document.querySelector('[data-rest-length]');
	const REST_END_KEY = 'workout.restEndsAt';
	const REST_TOTAL_KEY = 'workout.restTotal';
	const REST_LENGTH_KEY = 'workout.restLength';
	let restTimer = null;

	if (restLength) {
		const saved = storage.get(REST_LENGTH_KEY);
		if (saved !== null && restLength.querySelector(`option[value="${saved}"]`)) {
			restLength.value = saved;
		}
		restLength.addEventListener('change', () => storage.set(REST_LENGTH_KEY, restLength.value));
	}

	const beep = () => {
		try {
			const audio = new (window.AudioContext || window.webkitAudioContext)();
			[0, 0.25].forEach((offset) => {
				const osc = audio.createOscillator();
				const gain = audio.createGain();
				osc.frequency.value = 880;
				gain.gain.setValueAtTime(0.2, audio.currentTime + offset);
				gain.gain.exponentialRampToValueAtTime(0.001, audio.currentTime + offset + 0.2);
				osc.connect(gain).connect(audio.destination);
				osc.start(audio.currentTime + offset);
				osc.stop(audio.currentTime + offset + 0.2);
			});
		} catch { /* no audio available */ }
		if (navigator.vibrate) navigator.vibrate([200, 100, 200]);
	};

	const stopRest = () => {
		clearInterval(restTimer);
		restTimer = null;
		storage.set(REST_END_KEY, null);
		if (rest) rest.classList.add('hidden');
	};

	const renderRest = () => {
		const endsAt = Number(storage.get(REST_END_KEY));
		const total = Number(storage.get(REST_TOTAL_KEY)) || 1;
		const left = (endsAt - Date.now()) / 1000;
		if (!endsAt || left <= 0) {
			if (endsAt) {
				beep();
				rest.querySelector('[data-rest-label]').textContent = 'Rest over';
				rest.querySelector('[data-rest-time]').textContent = 'Go!';
				rest.querySelector('[data-rest-ring]').style.strokeDashoffset = 100;
				clearInterval(restTimer);
				restTimer = null;
				storage.set(REST_END_KEY, null);
				setTimeout(() => { if (!restTimer) rest.classList.add('hidden'); }, 4000);
			}
			return;
		}
		rest.querySelector('[data-rest-label]').textContent = 'Rest';
		rest.querySelector('[data-rest-time]').textContent = formatClock(Math.ceil(left));
		rest.querySelector('[data-rest-ring]').style.strokeDashoffset = 100 - Math.min(100, (left / total) * 100);
	};

	const runRest = () => {
		rest.classList.remove('hidden');
		clearInterval(restTimer);
		restTimer = setInterval(renderRest, 250);
		renderRest();
	};

	const startRest = () => {
		const seconds = Number(restLength ? restLength.value : 90);
		if (!rest || !seconds) return;
		storage.set(REST_END_KEY, String(Date.now() + seconds * 1000));
		storage.set(REST_TOTAL_KEY, String(seconds));
		runRest();
	};

	if (rest) {
		// Survive a page reload (e.g. after adding a set) mid-rest.
		if (Number(storage.get(REST_END_KEY)) > Date.now()) runRest();

		rest.querySelector('[data-rest-skip]').addEventListener('click', stopRest);
		rest.querySelectorAll('[data-rest-adjust]').forEach((button) => button.addEventListener('click', () => {
			const delta = Number(button.dataset.restAdjust) * 1000;
			const endsAt = Number(storage.get(REST_END_KEY)) || Date.now();
			const next = Math.max(Date.now() + 1000, endsAt + delta);
			storage.set(REST_END_KEY, String(next));
			storage.set(REST_TOTAL_KEY, String(Math.max(Number(storage.get(REST_TOTAL_KEY)) || 0, (next - Date.now()) / 1000)));
			runRest();
		}));
	}

	// ----- Saving sets in the background -----
	const updateProgress = (data) => {
		const done = document.querySelector('[data-progress-done]');
		if (!done) return;
		done.textContent = data.doneSets;
		document.querySelector('[data-progress-total]').textContent = data.totalSets;
		document.querySelector('[data-progress-volume]').textContent = Number(data.volume).toLocaleString('en-US');
		const pct = data.totalSets ? (data.doneSets / data.totalSets) * 100 : 0;
		document.querySelector('[data-progress-bar]').style.width = `${pct}%`;
		const records = new Set(data.records.map(String));
		document.querySelectorAll('[data-set-form]').forEach((row) => {
			row.dataset.pr = String(records.has(row.dataset.setId));
		});
	};

	// Renumber "1, 2, 3 / W" labels in a card after a warm-up toggle.
	const relabel = (card) => {
		let number = 0;
		card.querySelectorAll('[data-set-form]').forEach((row) => {
			row.querySelector('.set-label').textContent = row.dataset.warmup === 'true' ? 'W' : String(++number);
		});
	};

	const saveSet = async (form, submitter) => {
		const body = new FormData(form);
		if (submitter && submitter.name) body.set(submitter.name, submitter.value);
		try {
			const response = await fetch(form.action, {
				method: 'POST',
				body,
				headers: { 'X-Requested-With': 'fetch', Accept: 'application/json' },
				credentials: 'same-origin',
			});
			if (!response.ok || !(response.headers.get('content-type') || '').includes('json')) {
				throw new Error(`Save failed (${response.status})`);
			}
			const data = await response.json();
			const wasDone = form.dataset.done === 'true';
			form.dataset.done = String(data.completed);
			form.dataset.warmup = String(data.warmup);
			relabel(form.closest('section'));
			updateProgress(data);
			if (data.completed && !wasDone) startRest();
		} catch {
			// Session expired or offline: fall back to a normal submit so nothing is silently lost.
			form.dataset.fallback = 'true';
			if (submitter) form.requestSubmit(submitter); else form.submit();
		}
	};

	document.querySelectorAll('[data-set-form]').forEach((form) => {
		form.addEventListener('submit', (event) => {
			const submitter = event.submitter;
			// Deleting a set is a real navigation; let it through.
			if (submitter && submitter.hasAttribute('formaction')) return;
			if (form.dataset.fallback) return;
			event.preventDefault();
			saveSet(form, submitter).catch(() => {});
		});
		form.querySelectorAll('input[type="number"]').forEach((input) => {
			input.addEventListener('change', () => saveSet(form, null));
			// Pick the whole number on focus so you can type over it with one hand.
			input.addEventListener('focus', () => input.select());
			// Enter jumps to the next box (blurring saves this one) instead of submitting.
			input.addEventListener('keydown', (event) => {
				if (event.key !== 'Enter') return;
				event.preventDefault();
				const inputs = [...document.querySelectorAll('[data-set-form] input[type="number"]')];
				const next = inputs[inputs.indexOf(input) + 1];
				if (next) next.focus(); else input.blur();
			});
		});
	});

	// ----- Search + muscle-group filter (exercise picker and library) -----
	document.querySelectorAll('[data-picker]').forEach((picker) => {
		const search = picker.querySelector('[data-picker-search]');
		const empty = picker.querySelector('[data-picker-empty]');
		const groupButtons = picker.querySelectorAll('[data-picker-group]');
		let group = '';

		const apply = () => {
			const terms = (search ? search.value : '').toLowerCase().trim().split(/\s+/).filter(Boolean);
			let shown = 0;
			picker.querySelectorAll('[data-picker-item]').forEach((item) => {
				const section = item.closest('[data-picker-section]');
				const inGroup = !group || (section && section.dataset.pickerSection === group);
				const match = inGroup && terms.every((term) => item.dataset.pickerItem.includes(term));
				item.classList.toggle('hidden', !match);
				if (match) shown++;
			});
			picker.querySelectorAll('[data-picker-section]').forEach((section) => {
				section.classList.toggle('hidden', !section.querySelector('[data-picker-item]:not(.hidden)'));
			});
			if (empty) empty.classList.toggle('hidden', shown > 0);
		};

		if (search) search.addEventListener('input', apply);
		groupButtons.forEach((button) => button.addEventListener('click', () => {
			group = button.dataset.pickerGroup;
			groupButtons.forEach((b) => b.classList.toggle('active', b === button));
			apply();
		}));
	});

	// ----- Chart tooltip + crosshair -----
	document.querySelectorAll('[data-chart]').forEach((chart) => {
		const svg = chart.querySelector('svg');
		const tip = chart.querySelector('[data-chart-tip]');
		const crosshair = chart.querySelector('[data-chart-crosshair]');
		const hide = () => { tip.classList.add('hidden'); crosshair.classList.add('hidden'); };
		chart.querySelectorAll('[data-chart-point]').forEach((point) => {
			const show = () => {
				const scale = svg.clientWidth / svg.viewBox.baseVal.width;
				const x = Number(point.getAttribute('cx'));
				const y = Number(point.getAttribute('cy'));
				tip.innerHTML = '';
				const date = document.createElement('span');
				date.className = 'text-muted';
				date.textContent = `${point.dataset.date} `;
				const value = document.createElement('span');
				value.className = 'text-zinc-100';
				value.textContent = point.dataset.value;
				tip.append(date, value);
				tip.style.left = `${x * scale}px`;
				tip.style.top = `${y * scale - 10}px`;
				tip.classList.remove('hidden');
				crosshair.setAttribute('x1', x);
				crosshair.setAttribute('x2', x);
				crosshair.classList.remove('hidden');
			};
			point.addEventListener('mouseenter', show);
			point.addEventListener('focus', show);
			point.addEventListener('click', show);
			point.addEventListener('mouseleave', hide);
		});
	});
})();
