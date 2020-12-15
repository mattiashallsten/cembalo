Cembalo {
	var <out, <tuning, <root, <amp;
	var server, path;
	var <bodyBuffers, <releaseBuffers;
	var <keys;
	classvar rates, acceptableTunings;
	classvar midiNoteOffset = 24, midiNoteCeil;


	// * Class method: new
	*new {|out = 0, tuning = \et12, root = 0, amp = 0.7|
		^super.newCopyArgs(out, tuning, root, amp).initCembalo;
	}

	// * Instance method: initCembalo
	initCembalo {
		server = Server.local;
		path = Platform.userExtensionDir ++ "/Cembalo/";

		acceptableTunings = ['et12', 'fivelimit', 'sevenlimit', 'pyth'];
		rates = 1!12;
		this.tuningSetup(tuning);

		server.waitForBoot{
			this.loadSynthDefs;
			this.loadBuffers;

			server.sync;

			midiNoteCeil = bodyBuffers.size - 1 + midiNoteOffset;
			keys = Array.newClear(bodyBuffers.size);
		}
	}

	// * Instance method: keyOn
	keyOn {|key = 0, rate = 1, pan = 0, amp = 0.7|
		if(key < bodyBuffers.size, {
			if(keys[key].notNil, {
				this.keyOff(key, rate);
				this.keyOn(key, rate);
			}, {
				keys[key] = Synth(\cembalo_player, [
					\buf, bodyBuffers[key],
					\out, out,
					\rate, rate,
					\pan, pan,
					\amp, amp
				]);
			})
		});
	}

	// * Instance method: keyOff
	keyOff {|key = 0, rate = 1, pan = 0, amp = 0.7|
		if(key < bodyBuffers.size, {
			if(keys[key].notNil, {
				keys[key].set(\gate, 0);
				keys[key] = nil;
				Synth(\cembalo_player_oneshot, [
					\buf, releaseBuffers[key],
					\out, out,
					\rate, rate,
					\pan, pan,
					\amp, amp
				])
			})
		});
	}

	// * Instance method: playMIDINote
	playMIDINote {|note = 60, dur = 4, strum = 0, randomStrum = false, panDispersion = 0|
		var transposedRates = rates.rotate(root % 12);
		
		note = note.asArray;

		if(randomStrum, {note = note.scramble});

		note.do{|note, index|
			var pan = ((panDispersion * 100).rand / 100) * [1,-1].choose;
			var localDelay = strum * index;
			var local_body;
			if(note < midiNoteOffset, {
				"Can only play down to MIDI note %".format(midiNoteOffset).postln;
			}, {
				if(note > midiNoteCeil, {
					"Can only play up to MIDI note %".format(midiNoteCeil).postln;
				}, {
					if((bodyBuffers[note - midiNoteOffset].notNil) && (releaseBuffers[note - midiNoteOffset].notNil), {
						fork {
							wait(localDelay);
							// local_body = Synth(\cembalo_player, [
							// 	\buf, bodyBuffers[note - midiNoteOffset],
							// 	\out, out,
							// 	\rate, transposedRates[note % 12],
							// 	\pan, pan,
							// 	\amp, amp
							// ]);
							this.keyOn(note - midiNoteOffset, transposedRates[note % 12]);
							wait(dur - localDelay);
							this.keyOff(note - midiNoteOffset, transposedRates[note % 12]);
							keys.postln;
							// local_body.set(\gate, 0);
							// Synth(\cembalo_player_oneshot, [
							// 	\buf, releaseBuffers[note - midiNoteOffset],
							// 	\out, out,
							// 	\rate, transposedRates[note % 12],
							// 	\pan, pan,
							// 	\amp, amp
							// ])
						}
					})
				})
			});
		}
	}

	// * Instance method: playNote
	playNote {|freq = 440, dur = 4, strum = 0, randomStrum = false, panDispersion = 0|
		freq = freq.asArray;

		if(randomStrum, {freq = freq.scramble});
		
		freq.do{|freq, index|
			var pan = ((panDispersion * 100).rand / 100) * [1,-1].choose;
			var localDelay = strum * index;
			var asMidi = freq.cpsmidi;
			var bufIndex = asMidi.ceil.asInteger - midiNoteOffset;
			var rate = (asMidi - asMidi.ceil).midiratio;
			var local_body;

			bufIndex.postln;


			fork {
				wait(localDelay);
				// local_body = Synth(\cembalo_player, [
				// 	\buf, bodyBuffers[bufIndex.asInteger - midiNoteOffset],
				// 	\out, out,
				// 	\rate, rate,
				// 	\pan, pan,
				// 	\amp, amp
				// ]);
				this.keyOn(bufIndex, rate, pan, amp);
				wait(dur - localDelay);
				// local_body.set(\gate, 0);

				// Synth(\cembalo_player_oneshot, [
				// 	\buf, releaseBuffers[bufIndex.asInteger - midiNoteOffset],
				// 	\out, out,
				// 	\rate, rate,
				// 	\pan, pan,
				// 	\amp, amp
				// ]);
				this.keyOff(bufIndex, rate, pan, amp);
				keys.postln;
			}
		}
	}

	root_{|newRoot|
		root = newRoot % 12;
	}

	tuning_{|newTuning|
		tuning = newTuning;
		this.tuningSetup(tuning);
	}

	amp_{|newAmp|
		amp = newAmp;
	}

	// * Instance method: tuningSetup
	tuningSetup {|tuning|
		if(this.arrayContains(acceptableTunings, tuning), {
			rates = switch(tuning,
				'et12', {1!12},
				'fivelimit', {[
					1,
					(16/15) / 1.midiratio,
					(9/8) / 2.midiratio,
					(6/5) / 3.midiratio,
					(5/4) / 4.midiratio,
					(4/3) / 5.midiratio,
					(45/32) / 6.midiratio,
					(3/2) / 7.midiratio,
					(8/5) / 8.midiratio,
					(5/3) / 9.midiratio,
					(9/5) / 10.midiratio,
					(15/9) / 11.midiratio
				]},
				'sevenlimit', {[
					1,
					(16/15) / 1.midiratio,
					(9/8) / 2.midiratio,
					(7/6) / 3.midiratio,
					(9/7) / 4.midiratio,
					(4/3) / 5.midiratio,
					(45/32) / 6.midiratio,
					(3/2) / 7.midiratio,
					(8/5) / 8.midiratio,
					(5/3) / 9.midiratio,
					(7/4) / 10.midiratio,
					(15/9) / 11.midiratio
				]},
				'pyth', {[
					1,
					(256/243) / 1.midiratio,
					(9/8) / 2.midiratio,
					(32/27) / 3.midiratio,
					(81/64) / 4.midiratio,
					(4/3) / 5.midiratio,
					(729/512) / 6.midiratio,
					(3/2) / 7.midiratio,
					(128/81) / 8.midiratio,
					(27/16) / 9.midiratio,
					(16/9) / 10.midiratio,
					(243/128) / 11.midiratio
				]},
				{1!12}
			);
		}, {
			"Tuning % not valid".format(tuning).postln;
		});
	}

	// * Instance method: arrayContains
	arrayContains {|array,value|
		var contains = false;
		array.asArray.do{|item|
			if(item == value, {contains = true})
		};

		^contains
	}

	// * Instance method: loadSynthDefs
	loadSynthDefs {
		// * SynthDef: cembalo_player
		SynthDef(\cembalo_player, {
			| buf = 0
			, gate = 1
			, out = 0
			, rate = 1
			, pan = 0
			, amp = 1
			|

			var env = EnvGen.kr(Env.asr(0,1,0), gate, doneAction:2);
			var sig = PlayBuf.ar(2, buf, rate) * env;

			sig = Balance2.ar(sig[0], sig[1], pan);

			sig = sig * amp;

			Out.ar(out, sig)
		}).add;

		// * SynthDef: cembalo_player_oneshot
		SynthDef(\cembalo_player_oneshot, {
			| buf = 0
			, out = 0
			, rate = 1
			, pan = 0
			, amp = 1
			|

			var sig = PlayBuf.ar(2, buf, rate, doneAction:2);
			sig = Balance2.ar(sig[0], sig[1], pan);

			sig = sig * amp;

			Out.ar(out, sig)
		}).add;
	}

	// * Instance method: loadBuffers
	loadBuffers {
		var bodyPath = path ++ "samples/bod/*.wav";
		var releasePath = path ++ "samples/rel/*.wav";

		bodyBuffers = bodyPath.pathMatch.collect{|filePath|
			Buffer.read(server, filePath)
		};

		releaseBuffers = releasePath.pathMatch.collect{|filePath|
			Buffer.read(server, filePath)
		};
	}
}
// Local Variables:
// eval: (outshine-mode 1)
// End: