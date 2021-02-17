Cembalo {
	var <out, <tuning, <root, <amp, <masterRate, <outputmapping, <mixToMono;
	var server, path;
	var <bodyBuffers, <releaseBuffers;
	var <keys;
	var rates, transposedRates, acceptableTunings;
	var <midiNoteOffset = 24, midiNoteCeil;
	var keysPressedIndex;
	var currentChord, chordOctave = 0;
	var bodySynthdef, releaseSynthdef;

	// * Class method: new
	*new {|out = 0, tuning = \et12, root = 0, amp = 0.7, masterRate = 1, outputmapping = 0, mixToMono = false|
		^super.newCopyArgs(out, tuning, root, amp, masterRate, outputmapping, mixToMono).initCembalo;
	}

	// * Instance method: initCembalo
	initCembalo {
		server = Server.local;
		path = Platform.userExtensionDir ++ "/cembalo/";

		acceptableTunings = ['et12', 'fivelimit', 'sevenlimit', 'pyth', 'mean'];
		rates = 1!12;

		outputmapping = this.outputMappingSetup(outputmapping);

		if(mixToMono, {
			bodySynthdef = \cembalo_player_mono;
			releaseSynthdef = \cembalo_player_oneshot_mono;
		}, {
			bodySynthdef = \cembalo_player;
			releaseSynthdef = \cembalo_player_oneshot;
		});

		bodySynthdef.postln;
		
		this.tuningSetup(tuning);

		server.waitForBoot{
			this.loadSynthDefs;
			this.loadBuffers;

			server.sync;

			midiNoteCeil = bodyBuffers.size - 1 + midiNoteOffset;

			keysPressedIndex = Array.fill(bodyBuffers.size, {0});

			keys = Array.fill(bodyBuffers.size, {|i|
				var output = outputmapping[i%outputmapping.size];
				
				CembaloKey(
					nn: i + midiNoteOffset,
					out: output[0],
					outL: output[0],
					outR: output[1],
					amp: amp,
					pan: 0,
					bodyBuffer: bodyBuffers[i],
					releaseBuffer: releaseBuffers[i],
					bodySynthdef: bodySynthdef,
					releaseSynthdef: releaseSynthdef
				)
			});

			currentChord = Array.fill(keys.size, {0});

			this.eventTypeSetup;
		}
	}

	getMax {
		^midiNoteCeil
	}
	
	getMin {
		^midiNoteOffset
	}

	// * Instance method: keyOn
	keyOn {|key = 0, pan = 0, amp = 0.7, rate|
		if(rate == nil, {
			rate = transposedRates[(key + midiNoteOffset) % 12];
		});
		if((key < keys.size) && (key >= 0), {
			keys[key].keyOn(rate * masterRate, amp, pan)
		});
	}

	// * Instance method: keyOff
	keyOff {|key = 0, pan = 0, amp = 0.7|
		if((key < keys.size) && (key >= 0), {
			keys[key].keyOff
		});
	}

	// * Instance method: noteOn
	noteOn {|note = 60, pan = 0, amp = 0.7|
		this.keyOn(note - midiNoteOffset, pan, amp)
	}

	// * Instance method: noteOff
	noteOff {|note = 60, pan = 0, amp = 0.7|
		this.keyOff(note - midiNoteOffset, pan, amp)
	}

	// * Instance method: bendKey
	bendKey {|key = 0, val = 0|
		if(key < keys.size, { keys[key].bend(val) } );
	}

	// * Instance method: playMIDINote
	playMIDINote {|note = 60, dur = 4, strum = 0, randomStrum = false, randomRelease = 0, panDispersion = 0, randTimeAm = 0|
		note = note.asArray;

		if(randomStrum, {note = note.scramble});

		note.do{|note, index|
			var pan = ((panDispersion * 100).rand / 100) * [1,-1].choose;
			var localDelay = strum * index * rrand(1 - randTimeAm, 1 + randTimeAm);
			var local_body;
			if(note < midiNoteOffset, {
				"Can only play down to MIDI note %".format(midiNoteOffset).postln;
			}, {
				if(note > midiNoteCeil, {
					"Can only play up to MIDI note %".format(midiNoteCeil).postln;
				}, {
					if(
						(bodyBuffers[note - midiNoteOffset].notNil) &&
						(releaseBuffers[note - midiNoteOffset].notNil), {
							fork {

								// The `keysPressedIndex' and `localIndex'
								// variables work like this:

								// The `keysPressedIndex' variable is an array,
								// initialized to contain only zeros and have
								// the same length as the number of buffers (the
								// number of keys). Each time a key is played
								// using the timed methods (.playMIDINote and
								// .playNote), the value of this array at the
								// index of the key is read and stored at
								// `localIndex', in order to now what "event" is
								// being played. The value is then increased by
								// 1, so that the next "event" will have a new
								// index. When it is time to turn off the note,
								// it checks to see if we are in fact on the
								// same event or if a new event has happened
								// before the first one is finished. If a new
								// event is happening, it doesn't bother with
								// turning the note off.
								
								var localIndex = keysPressedIndex[note - midiNoteOffset].copy;
								keysPressedIndex[note - midiNoteOffset] = keysPressedIndex[note - midiNoteOffset] + 1;

								wait(localDelay);
								
								this.keyOn(note - midiNoteOffset, pan, amp);

								wait((dur - localDelay) * rrand(1 - randomRelease, 1 + randomRelease));

								if(keysPressedIndex[note - midiNoteOffset] - 1 == localIndex, {
									this.keyOff(note - midiNoteOffset, pan, amp);
								}, {"key depressed again".postln});
							}
					})
				})
			});
		}
	}

	// * Instance method: playNote
	playNote {|freq = 440, dur = 4, strum = 0, randomStrum = false, randomRelease = 0, panDispersion = 0|
		freq = freq.asArray;

		if(randomStrum, {freq = freq.scramble});
		
		freq.do{|freq, index|
			var pan = ((panDispersion * 100).rand / 100) * [1,-1].choose;
			var localDelay = strum * index;
			var asMidi = freq.cpsmidi;
			var bufIndex = asMidi.ceil.asInteger - midiNoteOffset;
			var rate = (asMidi - asMidi.ceil).midiratio;
			var local_body;

			fork {
				// Documented in the method `playMIDINote'.
				
				var localIndex = keysPressedIndex[bufIndex].copy;
				keysPressedIndex[bufIndex] = keysPressedIndex[bufIndex] + 1;

				wait(localDelay);

				this.keyOn(bufIndex, rate: rate, pan: pan, amp: amp);

				wait((dur - localDelay) * rrand(1-randomRelease,1+randomRelease));

				if(keysPressedIndex[bufIndex] - 1 == localIndex, {
					this.keyOff(bufIndex, pan, amp);
				});
			}
		}
	}

	printChord {
		var nn = [];
		currentChord.do{|item, index|
			if(item == 1, {
				nn = nn.add(index + midiNoteOffset)
			})
		};
		
		"Current chord: %".format(nn).postln;
	}

	chordGate {|gate|
		if(gate == 1, {
			var nn = [];

			currentChord.do{|item, index|
				if(item == 1, {
					nn = nn.add(index + midiNoteOffset)
				})
			};
			this.playChord(nn)
		}, {
			this.keys.do{|item,index| this.keyOff(index)}
		})
	}
	
	// * Instance method: playChord
	playChord {|nn, repeat=false|

		// The user supplies an array of notes, and the Cembalo plays those
		// notes. The Cembalo saves the notes as `currentChord', so it knows
		// what notes to repeat and what notes to simply change if a new chord
		// is specified (i.e only turn off G and turn on A when changing from C
		// major to A minor).

		// However, the CembaloKey class has a timeout feature that wait until
		// the body sound has faded out. If it has, the `playChord' method
		// automatically repeats that note. This might be changed in the future.
		
		var newChord;
		nn = nn.asArray.collect{|item| item - midiNoteOffset};

		newChord = 0!keys.size;
		nn.do{|item|
			newChord[item] = 1
		};

		newChord.do{|item, index|
			if(item == 1, {
				if(
					(currentChord[index] == 0) ||
					(repeat) ||
					(keys[index].player == nil), {
						this.keyOn(index, rate: transposedRates[(nn + midiNoteOffset) % 12])
					})
			}, {
				if(currentChord[index] == 1, {
					this.keyOff(index)
				})
			})
		};

		currentChord = newChord.copy;
		this.printChord();
	}

	// * Instance method: repeatChord
	repeatChord {|randomize = 0|

		// Simply repeating the current chord.
		
		currentChord.do{|item, index|
			if(item == 1, {
				fork {
					wait(rrand(0, randomize));
					this.keyOn(index);
				}
			}, {
				this.keyOff(index)
			})
		};

		this.printChord();
	}

	// * Instance method: transformChord
	transformChord {|function|
		if(function.isFunction, {
			var nn = [];

			currentChord.collect{|item, index|
				if(item == 1, {
					nn = nn.add(index + midiNoteOffset)
				})
			};

			this.playChord(function.value(nn));
			
		})
	}

	// * Instance method: addToChord
	addToChord {|nn, repeat = true|

		// Add a note to the current playing chord. If it is already there, it
		// just repeats it.

		nn = nn.asArray.collect{|item| item - midiNoteOffset };

		nn.do{|item|
			if((currentChord[item] == 0) || (repeat == true), {
				currentChord[item] = 1;
				this.keyOn(item, rate: transposedRates[(nn + midiNoteOffset) % 12]);
			})
		};

		this.printChord();
	}

	removeFromChord {|nn|
		nn = nn.asArray.collect{|item| item - midiNoteOffset };

		nn.do{|item|
			currentChord[item] = 0;
			this.keyOff(item);
		};

		this.printChord();
	}

	allKeysOff {
		this.keys.do{|item, index|
			this.keyOff(index)
		}
	}
	
	root_{|newRoot|
		root = newRoot % 12;
		this.tuningSetup(tuning)
	}

	tuning_{|newTuning|
		tuning = newTuning;
		this.tuningSetup(tuning);
	}

	amp_{|newAmp|
		amp = newAmp;
	}

	masterRate_{|newMasterRate|
		masterRate = newMasterRate
	}

	// * Instance method: outputMappingSetup
	outputMappingSetup {|array|
		var newArray;
		var detailed = array.isArray;

		array = array.asArray;

		array.do{|item, index|
			if(item.isArray, {
				if(item.size > 1, {
					newArray = newArray.add([item[0], item[1]])
				}, {
					newArray = newArray.add([item[0], item[0]])
				})
			}, {
				if(detailed, {
					newArray = newArray.add([item, item])
				}, {
					newArray = newArray.add([item, item + 1])
				})
			})
		};
		
		^newArray
	}

	// * Instance method: eventTypeSetup
	eventTypeSetup {
		Event.removeEventType(\cembalo);
		Event.addEventType(\cembalo, {
			if(~cembalo.notNil, {
				~play = ~cembalo.playNote(
					freq: ~freq.value,
					dur: ~sustain.value,
					strum: ~strum.value,
					randomStrum: ~randomStrum,
					randomRelease: ~randomRelease,
					panDispersion: ~panDispersion
				)
			}, {
				~play = "You have to supply an instace of Cembalo".postln
			})
		}, (randomStrum: false, randomRelease: 0, panDispersion: 0))
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
					(15/8) / 11.midiratio
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
					(15/8) / 11.midiratio
				]},
				'pyth', {[
					1,					      // C
					(256/243) / 1.midiratio,  // Db
					(9/8) / 2.midiratio,	  // D
					(32/27) / 3.midiratio,	  // Eb
					(81/64) / 4.midiratio,	  // E
					(4/3) / 5.midiratio,	  // F
					(729/512) / 6.midiratio,  // F#
					(3/2) / 7.midiratio,	  // G
					(128/81) / 8.midiratio,	  // Ab
					(27/16) / 9.midiratio,	  // A
					(16/9) / 10.midiratio,	  // Bb
					(243/128) / 11.midiratio  // B
				]},
				'mean', {[
					1,					             // C
					(8 / 5.pow(5/4)) / 1.midiratio,	 // C#
					(5.pow(1/2) / 2) / 2.midiratio,	 // D
					(4 / 5.pow(3/4)) / 3.midiratio,	 // Eb
					(5/4) / 4.midiratio,			 // E
					(2 / 5.pow(1/4)) / 5.midiratio,	 // F
					(5.pow(6/4) / 8) / 6.midiratio,	 // F#
					5.pow(1/4) / 7.midiratio,		 // G
					8/5 / 8.midiratio,				 // Ab
					(5.pow(3/4) / 2) / 9.midiratio,	 // A
					(4 / 5.pow(1/2)) / 10.midiratio, // Bb
					(5.pow(5/4) / 4) / 11.midiratio, // B
					
				]},
				{1!12}
			);
		}, {
			if(tuning.isArray, {
				var len = tuning.size;
				if(len < 12, {
					var diff = 12 - len;
					"not enough ratios: adding % 2".format(diff).postln;
					diff.do{
						tuning = tuning.add(2);
					}
				}, {
					if(len > 12, {
						var diff = len - 12;
						"too many ratios! removing % ratios".format(diff).postln;
						diff.do{|index|
							tuning.removeAt(len - index - 1)
						};
					});
				});

				rates = tuning.collect{|item, index|
					if(index == 0, {
						1
					}, {
						item / index.midiratio
					})
				}
			}, {
				"Tuning % not valid".format(tuning).postln;
			})
		});

		transposedRates = rates.rotate(root % 12);
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
			, outL = 0
			, outR = 1
			, rate = 1
			, pan = 0
			, amp = 1
			|

			var env = EnvGen.kr(Env.asr(0,1,0), gate, doneAction:2);
			var sig = PlayBuf.ar(2, buf, rate.lag() * BufRateScale.kr(buf)) * env;

			sig = Balance2.ar(sig[0], sig[1], pan);

			sig = sig * amp;

			Out.ar(outL, sig[0]);
			Out.ar(outR, sig[1])
		}).add;

		// * SynthDef: cembalo_player_oneshot
		SynthDef(\cembalo_player_oneshot, {
			| buf = 0
			, outL = 0
			, outR = 1
			, rate = 1
			, pan = 0
			, amp 1
			|

			var sig = PlayBuf.ar(2, buf, rate, doneAction:2);
			sig = Balance2.ar(sig[0], sig[1], pan);
			sig = sig * amp;

			Out.ar(outL, sig[0]);
			Out.ar(outR, sig[1]);
		}).add;

		// * SynthDef: cembalo_player_mono
		SynthDef(\cembalo_player_mono, {
			| buf = 0
			, gate = 1
			, out = 0
			, rate = 1
			, pan = 0
			, amp = 1
			|

			var env = EnvGen.kr(Env.asr(0,1,0), gate, doneAction:2);
			var sig = PlayBuf.ar(2, buf, rate.lag() * BufRateScale.kr(buf)) * env;

			//sig = Balance2.ar(sig[0], sig[1], pan);
			sig = Mix(sig);

			sig = sig * amp;

			Out.ar(out, sig)
		}).add;

		// * SynthDef: cembalo_player_oneshot_mono
		SynthDef(\cembalo_player_oneshot_mono, {
			| buf = 0
			, out = 0
			, rate = 1
			, pan = 0
			, amp 1
			|

			var sig = PlayBuf.ar(2, buf, rate, doneAction:2);
			//sig = Balance2.ar(sig[0], sig[1], pan);
			sig = Mix(sig);
			sig = sig * amp;

			Out.ar(out, sig)
		}).add
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