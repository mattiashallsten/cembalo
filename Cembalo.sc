Cembalo {
	var <out, <tuning, <root, <amp, <masterRate, <outputmapping, <mixToMono;
	var server, path;
	var <bodyBuffers, <releaseBuffers;
	var <keys;
	var rates, transposedRates, acceptableTunings;
	var <midiNoteOffset = 24, midiNoteCeil;
	var keyEventIndex;
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

			keyEventIndex = Array.fill(bodyBuffers.size, {0});

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

		// This is how to interact with the `keyOn' method within the
		// `CembaloKey' class on the lowest abstraction level. The method
		// `makeKeyEvet' interfaces with this method.
		
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

	// * Instance method: makeKeyEvent
	makeKeyEvent {|key = 0, dur = 4, delay = 0, pan = 0, rate|

		// One level of abstraction up from `keyOn'. The user supplies a key,
		// a duration, a delay value, a pan value. If the key should be
		// fine-tuned the user can also supply a rate value, if not the
		// `keyOn' method adheres to the selected temperament.
		
		if(key < 0, {
			"Too low index!".postln
		}, {
			if(key > keys.size, {
				"Too high index!".postln
			}, {
				fork {

					// By using an array of integers, I can keep track of if
					// the current event for the key has been interrupted by a
					// new event. Everytime this method is invoked, the value
					// increases by one.
					
					var localIndex = keyEventIndex[key].copy;
					keyEventIndex[key] = keyEventIndex[key] + 1;

					wait(delay);

					// the `keyOn' method checks if the rate is set to
					// nil. if it is, it will do all the necessary
					// transpositions for the different temperaments.
					this.keyOn(key, pan, rate:rate);

					wait(dur - delay);

					if(keyEventIndex[key] - 1 == localIndex, {
						this.keyOff(key)
					})
				}
			})
		})
	}

	// * Instance method: playMIDINote
	playMIDINote {|note = 60, dur = 4, pan = 0, strum = 0, randomStrum = false|

		// One level of abstraction up from `makeKeyEvent'. The user supplies
		// a MIDI note (can be an array), a duration, a panning value (can be
		// an array), a strum value (how much time there should be between
		// multiple notes), and a randomStrum value (whether or not to shuffle
		// the notes when strumming).
		
		var key = note.asArray.collect{|item|
			item - midiNoteOffset
		};

		var delay = key.collect{|item, index|
			index * strum
		};

		if(randomStrum, {delay = delay.scramble});

		pan = pan.asArray;
		dur = dur.asArray;

		key.do{|item, index|
			this.makeKeyEvent(
				item,
				dur[index % dur.size],
				delay[index],
				pan[index % pan.size]
			)
		}
	}

	// * Instance method: playNote
	playNote {|freq = 440, dur = 4, pan = 0, strum = 0, randomStrum = false|

		// As with `playMIDINote': one level of abstraction higher than
		// `makeKeyEvent'. Instead of a midi note number, the user here
		// specifies a frequency in hertz.
		
		var delay, key, rate;

		key = [];						// one array for the key index
		rate = [];						// one array for rates

		freq.asArray.do{|item, index|
			// convert the frequency value to midi (with decimals)
			var asMIDI = item.cpsmidi;
			// convert the midi value to integer, i.e actual midi note numbers
			var midiNN = asMIDI.floor.asInteger;
			// add the key index by subtracting the midi note offset
			key = key.add(midiNN - midiNoteOffset);
			// add the rate value by diffing the floored midi value with the
			// original midi value, and then converting it to a ratio value
			rate = rate.add((asMIDI - midiNN).midiratio)
		};
		
		delay = key.collect{|item, index|
			index * strum
		};

		if(randomStrum, {delay = delay.scramble});

		pan = pan.asArray;
		dur = dur.asArray;

		key.do{|item, index|
			this.makeKeyEvent(
				item,
				dur[index % dur.size],
				delay[index],
				pan[index % pan.size],
				rate[index]
			)
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
	playChord {|nn, repeat=false, strum=0|

		// The user supplies an array of notes, and the Cembalo plays those
		// notes. The Cembalo saves the notes as `currentChord', so it knows
		// what notes to repeat and what notes to simply change if a new chord
		// is specified (i.e only turn off G and turn on A when changing from C
		// major to A minor).

		// However, the CembaloKey class has a timeout feature that wait until
		// the body sound has faded out. If it has, the `playChord' method
		// automatically repeats that note. This might be changed in the future.
		
		var newChord;
		var delay = 0;
		nn = nn.asArray.collect{|item|
			item - midiNoteOffset
		};

		newChord = 0!keys.size;
		nn.do{|item|
			if(
				(item < newChord.size) &&
				(item >= 0),
				{ newChord[item] = 1; }
			)
		};

		newChord.do{|item, index|
			if(item == 1, {
				if(
					(currentChord[index] == 0) ||
					(repeat) ||
					(keys[index].player == nil), { // here is the timeout feature
						fork {
							var localDelay = delay.copy;
							delay = delay + strum;
							wait(localDelay);
							this.keyOn(index)

						}
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

	// * Instance method: tuning_
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
					pan: ~pan,
					randomStrum: ~randomStrum,
				);
			}, {
				~play = "You have to supply an instace of Cembalo".postln
			})
		}, (randomStrum: false))
	}

	// * Instance method: generateFifthBasedScale
	generateFifthBasedScale {|fractionOfComma=0|
		var scale = this.generateScale(
			(3/2) / (((3/2).pow(4) / 5).pow(fractionOfComma))
		);
		^scale
	}
	
	// * Instance method: generateScale
	generateScale {|fifth=1.5|
		var scale = [1];
		var i = 0;
		// first, scale goes up in fifths:

		// index:   0 1 2 3 4 5  6  7
		// name:    G D A E B F# C# G#
		while( {i < 8}, {
			var newRatio = scale[i] * fifth;
			scale = scale.add(newRatio);
			i = i + 1
		} );

		// the, scale goes down in fifths:

		// index:  7 8  9
		// name:   F Bb Eb
		while( {i < 11}, {
			if(i == 8, {
				var newRatio = 1 / fifth;
				scale = scale.add(newRatio);
				i = i + 1
			}, {
				var newRatio = scale[i] / fifth;
				scale = scale.add(scale[i] / fifth);
				i = i + 1
				
			});
		});
		scale = scale.collect{|item|
			while({ (item > 2) || (item < 1) }, {
				if(item > 2, {
					item = item / 2
				}, {
					item = item * 2
				})
			});
			item
		};
		scale = scale.sort;
		^scale
	}

	// * Instance method: tuningSetup
	tuningSetup {|tuning|
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
			if(tuning.isSymbol, {
				switch(tuning,
					'et12', {rates = 1!12},
					'mean', {
						var scale = this.generateFifthBasedScale(1/4);
						this.tuningSetup(scale);
					},
					'mean6', {
						var scale = this.generateFifthBasedScale(1/6);
						this.tuningSetup(scale);
					},
					'pyth', {
						var scale = this.generateFifthBasedScale(0);
						this.tuningSetup(scale)
					},
					'fivelimit', {
						this.tuningSetup([
							1,
							16/15,
							9/8,
							6/5,
							5/4,
							4/3,
							45/32,
							3/2,
							8/5,
							5/3,
							9/5,
							15/8
						])
					},
					'sevenlimit', {
						this.tuningSetup([
							1,
							16/15,
							9/8,
							7/6,
							9/7,
							4/3,
							45/32,
							3/2,
							8/5,
							5/3,
							7/4,
							15/8
						])
					}, {
						"Tuning % not found! Using default et12.\n".postf(tuning);
						rates = 1!12
					}
				)
			}, {
				if(tuning.isNumber, {
					var scale = this.generateFifthBasedScale(tuning);
					this.tuningSetup(scale);
				}, {
					"Tuning % not valid".format(tuning).postln;
				})
			});
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