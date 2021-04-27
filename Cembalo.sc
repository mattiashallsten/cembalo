Cembalo {
	var <out, <tuning, <root, <rootFreq, <amp, <outputmapping, <mixToMono;
	var userSamplePath, fillLostSamples, attack, release, lagTime;
	var server, path;
	var <buffers, configurationPath, <configuration, <bodyindex;
	var <keys;
	var rates, masterRate, transposedRates, acceptableTunings, <tuningType, bufferIndexOffset = 0;
	var timbre = 0;
	var <midiNoteOffset = 24, <midiNoteCeil;
	var keyEventIndex;
	var currentChord, chordOctave = 0;
	var <bodySynthdef, <releaseSynthdef, <bodySynthdefMono, <releaseSynthdefMono;

	// *** Class method: new
	*new {
		| out = 0
		, tuning = \et12
		, root = 0
		, rootFreq = 440
		, amp = 0.7
		, outputmapping = 0
		, mixToMono = false
		, userSamplePath = nil
		, fillLostSamples = false
		, attack = 0
		, release = 0
		, lagTime = 0.1
		|
		
		^super.newCopyArgs(
			out,
			tuning,
			root,
			rootFreq,
			amp,
			outputmapping,
			mixToMono,
			userSamplePath,
			fillLostSamples,
			attack,
			release,
			lagTime
			
		).initCembalo;
	}

	// *** Instance method: initCembalo
	initCembalo {
		"            =============".postln;
		"            == Cembalo ==".postln;
		"            =============".postln;
		server = Server.local;
		path = Platform.userExtensionDir ++ "/cembalo/";

		acceptableTunings = ['et12', 'fivelimit', 'sevenlimit', 'pyth', 'mean'];
		rates = 1!12;
		masterRate = 1;

		outputmapping = this.outputMappingSetup(outputmapping);

		buffers = Dictionary();
		bodyindex = 0;

		if(mixToMono, {
			bodySynthdef = \cembalo_player_mix_to_mono;
			releaseSynthdef = \cembalo_player_oneshot_mix_to_mono;
			bodySynthdefMono = \cembalo_player_mono;
			releaseSynthdefMono = \cembalo_player_oneshot_mono;
		}, {
			bodySynthdef = \cembalo_player;
			releaseSynthdef = \cembalo_player_oneshot;
			bodySynthdefMono = \cembalo_player_mono_to_stereo;
			releaseSynthdefMono = \cembalo_player_oneshot_mono_to_stereo;
		});

		// Initialize scaleType variable -- will be set in tuningSetup
		"Setting up tuning...".postln;
		this.tuning_(tuning);
		"Done.".postln;

		keys = nil!128;
		keyEventIndex = 0!128;
		currentChord = 0!128;

		"Loading configuration file...".postln;
		this.loadConfiguration;
		"Done.".postln;

		server.waitForBoot{
			"Server booted.".postln;
			
			"Loading synthdefs...".postln;
			this.loadSynthDefs;
			"Done".postln;

			"Loading buffers...".postln;
			this.loadBuffers;
			server.sync;
			"Done".postln;

			this.adjustSampleOffset;

			// Apply configuration to cent offsets
			buffers.do{|item|
				var num = item[\nn].asString;

				item[\centOffset] = configuration["keys"][num]["cent"].asFloat;
			};

			"Loading keys...".postln;
			buffers.do{|item|
				var nn = item[\nn];

				var output = outputmapping[nn%outputmapping.size];

				keys[nn] = CembaloKey(
					nn: nn,
					out: output[0],
					outL: output[0],
					outR: output[1],
					amp: amp,
					pan: 0,
					attack: attack,
					release: release,
					lagTime: lagTime,
					bodyBuffer: item[\body][bodyindex],
					releaseBuffer: item[\release],
					bodyindex: bodyindex,
					parent: this.value()
				)
			};

			server.sync;

			"Done.".postln;

			if(fillLostSamples, {
				"Creating additional keys...".postln;
				(0..127).do{|nn|
					if(keys[nn] == nil, {
						var output = outputmapping[nn%outputmapping.size];
						var sampleindex = this.findClosestSample(nn);

						"creating new key with note number % using sample %\n".postf(nn, sampleindex);
						
						keys[nn] = CembaloKey(
							nn: nn,
							out: output[0],
							outL: output[0],
							outR: output[1],
							amp: amp,
							pan: 0,
							attack: attack,
							release: release,
							bodyBuffer: buffers[sampleindex][\body],
							releaseBuffer: buffers[sampleindex][\release],
							parent: this.value()
						);
					})
				};
				server.sync;
				"Done".postln;
			});

			this.eventTypeSetup;

			"Cembalo loaded.".postln;
		}
	}

	getMax {
		var index = 127;

		while ({buffers[index] == nil}, {
			if(buffers[index].notNil, {
				midiNoteCeil = index;
			}, {
				index = index - 1
			})
		});

		^midiNoteCeil
	}
	
	getMin {
		var index = 0;

		while ({buffers[index] == nil}, {
			if(buffers[index].notNil, {
				midiNoteOffset = index;
			}, {
				index = index + 1
			})
		});
		^midiNoteOffset
	}

	// *** Instance method: keyOn
	keyOn {|key = 60, pan = 0, amp = 0.7, rate, newTimbre, attack, release, out, bodyindex|

		// This is how to interact with the `keyOn' method within the
		// `CembaloKey' class on the lowest abstraction level. The method
		// `makeKeyEvet' interfaces with this method.

		key = key.clip(0,127);
		
		if(rate == nil, {
			rate = transposedRates[(key) % 12];
		});

		if(newTimbre == nil, {
			newTimbre = timbre
		});
		
		if(keys[key].notNil, {
			keys[key].keyOn(rate * masterRate, amp, pan, newTimbre, attack, release, out, bodyindex)
		}, {
			"MIDI note number % not available in current sample bank!\n".postf(key);
		});
	}

	// *** Instance method: keyOff
	keyOff {|key = 60, pan = 0, amp = 0.7, out|
		key = key.clip(0,127);
		if(keys[key].notNil, {
			keys[key].keyOff(out)
		}, {
			"MIDI note number % not available in current sample bank!\n".postf(key);
		});
	}

	// *** Instance method: sustainPedalOn
	sustainPedalOn {
		keys.do{|key| if(key.notNil, { key.sustainPedalOn() } ) };
	}

	// *** Instance method: sustainPedalOff
	sustainPedalOff {
		keys.do{|key| if(key.notNil, { key.sustainPedalOff() } ) };
	}

	// *** Instance method: bendKeyStep
	bendKeyStep {|key = 0, num = 0|
		var val;
		key = key.clip(0,128);
		num = num.round(1).asInteger;
		val = (transposedRates.wrapAt(key + num) * num.midiratio) / (transposedRates.wrapAt(key));
		if(keys[key].notNil, { keys[key].bend(val) } )
	}

	bendKeyStepAll {|num|
		127.do{|index|
			this.bendKeyStep(index, num)
		}
	}

	// *** Instance method: bendKey
	bendKey {|key = 0, val = 0|
		key = key.clip(0,128);
		if(keys[key].notNil, { keys[key].bend(val) } );
	}

	// *** Instance method: bendAllKeys
	bendAllKeys {|val = 0|
		keys.do{|item|
			if(item.notNil, {
				item.bend(val)
			})
		}
	}

	// // *** Instance method: lagTime_
	// setLagTime {|val = 0.1|
	// 	lagTime = val;
	// 	keys.do{|item|
	// 		item.setLagTime(lagTime)
	// 	}
	// }
	setLagTime {|val = 0.1|
		lagTime = val;
		keys.do{|item|
			if(item.notNil, {
				item.setLagTime(lagTime)
			})
		}
	}

	// *** Instance method: makeKeyEvent
	makeKeyEvent {|
		key = 60
		, dur = 4
		, delay = 0
		, pan = 0
		, rate
		, timbre
		, bendDelay = 1
		, bendAm = 1
		, newAttack
		, newRelease
		, newOut
		, newBodyindex
		|

		// One level of abstraction up from `keyOn'. The user supplies a key,
		// a duration, a delay value, a pan value. If the key should be
		// fine-tuned the user can also supply a rate value, if not the
		// `keyOn' method adheres to the selected temperament.

		newAttack = newAttack ? attack;
		newRelease = newRelease ? release;
		newBodyindex = newBodyindex ? bodyindex;
		
		key = key.clip(0,128);
		if(keys[key].notNil, {
			fork {

				// By using an array of integers, I can keep track of if
				// the current event for the key has been interrupted by a
				// new event. Everytime this method is invoked, the value
				// increases by one.
				
				var localIndex = keyEventIndex[key].copy;
				var localBendDelay = dur * bendDelay;

				keyEventIndex[key] = keyEventIndex[key] + 1;

				wait(delay);

				// the `keyOn' method checks if the rate is set to
				// nil. if it is, it will do all the necessary
				// transpositions for the different temperaments.
				this.keyOn(
					key,
					pan,
					rate: rate,
					newTimbre: timbre,
					attack: newAttack,
					release: newRelease,
					out: newOut,
					bodyindex: newBodyindex
				);
				
				wait(localBendDelay - delay);

				if(keyEventIndex[key] - 1 == localIndex, {
					this.bendKey(key, bendAm)
				});
				
				wait(dur - delay - localBendDelay);

				if(keyEventIndex[key] - 1 == localIndex, {
					this.keyOff(key, out: newOut)
				})
			}
		}, {
			"MIDI note % not available!\n".postf(key);
		})

	}

	// *** Instance method: playMIDINote
	playMIDINote {
		| note = 60
		, dur = 4
		, pan = 0
		, timbre
		, strum = 0
		, randomStrum = false
		, bendDelay = 0
		, bendAm = 1
		, attack
		, release
		, out
		, bodyindex
		|

		// One level of abstraction up from `makeKeyEvent'. The user supplies
		// a MIDI note (can be an array), a duration, a panning value (can be
		// an array), a strum value (how much time there should be between
		// multiple notes), and a randomStrum value (whether or not to shuffle
		// the notes when strumming).
		
		var key = note.asArray;

		var delay = key.collect{|item, index|
			index * strum
		};

		if(randomStrum, {delay = delay.scramble});

		pan = pan.asArray;
		dur = dur.asArray;

		if(attack.notNil, {
			attack = attack.asArray
		}, {
			attack = [nil]
		});

		if(release.notNil, {
			release = release.asArray
		}, {
			release = [nil]
		});

		key.do{|item, index|
			this.makeKeyEvent(
				item,
				dur[index % dur.size],
				delay[index],
				pan[index % pan.size],
				timbre: timbre,
				bendDelay: bendDelay,
				bendAm: bendAm,
				newAttack: attack[index % dur.size],
				newRelease: release[index % dur.size],
				newOut: out,
				newBodyindex: bodyindex
			)
		}
	}

	// *** Instance method: playNote
	playNote {
		| freq = 440
		, dur = 4
		, pan = 0
		, timbre
		, strum = 0
		, randomStrum = false
		, bendDelay = 1
		, bendAm = 1
		, attack
		, release
		, out
		, bodyindex
		|

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
			var midiNN = asMIDI.floor.asInteger.clip(0,127);
			// find the closest sample that can be used
			midiNN = this.findClosestSample(midiNN);
			// add the key index to the list of keys
			key = key.add(midiNN);
			// add the rate value by diffing the floored midi value
			// with the midi value, and then converting it to a ratio
			// value
			rate = rate.add((asMIDI - midiNN).midiratio)
		};
		
		delay = key.collect{|item, index|
			index * strum
		};

		if(randomStrum, {delay = delay.scramble});

		pan = pan.asArray;
		dur = dur.asArray;

		if(attack.notNil, {
			attack = attack.asArray
		}, {
			attack = [nil]
		});

		if(release.notNil, {
			release = release.asArray
		}, {
			release = [nil]
		});

		key.do{|item, index|
			this.makeKeyEvent(
				item,
				dur[index % dur.size],
				delay[index],
				pan[index % pan.size],
				rate[index],
				timbre: timbre,
				bendDelay: bendDelay,
				bendAm: bendAm,
				newAttack: attack[index % dur.size],
				newRelease: release[index % dur.size],
				newOut: out,
				newBodyindex: bodyindex
				
			)
		}
	}
	// *** Instance method: printChord
	printChord {
		var nn = [];
		currentChord.do{|item, index|
			if(item == 1, {
				nn = nn.add(index)
			})
		};
		
		"Current chord: %".format(nn).postln;
	}

	// *** Instance method: chordGate
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
	
	// *** Instance method: playChord
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
		nn = nn.asArray.collect{|item| item.clip(0,127)};

		// Although the user supplies an array of MIDI note numbers,
		// the program converts that into a list of 128 values -- if
		// the note is on, it's a 1 and if it's off, it's a 0.
		
		newChord = 0!128;
		nn.do{|item|
			if(keys[nn].notNil, {
				newChord[item] = 1;
			})
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

	// *** Instance method: repeatChord
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

	// *** Instance method: transformChord
	transformChord {|function|
		if(function.isFunction, {
			var nn = [];

			currentChord.collect{|item, index|
				if(item == 1, {
					nn = nn.add(index)
				})
			};

			this.playChord(function.value(nn));
			
		})
	}

	// *** Instance method: addToChord
	addToChord {|nn, repeat = true|

		// Add a note to the current playing chord. If it is already there, it
		// just repeats it.

		nn = nn.asArray;

		nn.do{|item|
			if((currentChord[item] == 0) || (repeat == true), {
				currentChord[item] = 1;
				this.keyOn(item);
			})
		};

		this.printChord();
	}

	// *** Instance method: removeFromChord
	removeFromChord {|nn|
		nn = nn.asArray;

		nn.do{|item|
			currentChord[item] = 0;
			this.keyOff(item);
		};

		this.printChord();
	}

	// *** Instance method: allKeysOff
	allKeysOff {
		this.keys.do{|item, index|
			this.keyOff(index)
		}
	}

	// *** Instance method: root_
	root_{|newRoot|

		// "root" in this case means a MIDI note number, i.e
		// specifying what is considered to be 1/1 when generating a
		// scale. It is /not/ the root frequency in hertz (i.e 440,
		// 415 etc)
		
		root = newRoot % 12;
		this.tuningSetup(tuning)
	}

	// *** Instance method: bodyindex_
	bodyindex_{|newBodyIndex|
		bodyindex = newBodyIndex.asInteger;
		keys.do{|key|
			if(key.notNil, {
				key.bodyindex_(bodyindex)
			})
		};
	}

	// *** Instance method: timbre_
	timbre_{|newTimbre|
		timbre = newTimbre
	}

	// *** Instance method: amp_
	amp_{|newAmp|
		amp = newAmp;
		keys.do{|item|
			if(item.notNil, {
				item.amp_(amp)
			})
		};
	}

	/// *** Instance method: attack_
	attack_{|val|
		attack = val;
		keys.do{|item|
			if(item.notNil, {
				item.attack_(attack)
			})
		}
	}

	// *** Instance method: release_
	release_{|val|
		release = val;
		keys.do{|item|
			if(item.notNil, {
				item.release_(release)
			})
		}
	}

	// *** Instance method: setSampleCentDeviation
	setSampleCentOffset {|sample=60, val=0|
		buffers[sample][\centOffset] = val
	}

	// *** Instance method: outputMappingSetup
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

	// *** Instance method: eventTypeSetup
	eventTypeSetup {
		Event.removeEventType(\cembalo);
		Event.removeEventType(\cembalomidi);
		"Setting up regular event type...".postln;
		Event.addEventType(\cembalo, {
			if(~cembalo.notNil, {
				~play = ~cembalo.playNote(
					freq: ~freq.value,
					dur: ~sustain.value,
					strum: ~strum.value,
					pan: ~pan,
					timbre: ~timbre,
					randomStrum: ~randomStrum,
					bendDelay: ~bendDelay,
					bendAm: ~bendAm,
					attack: ~attack,
					release: ~release,
					out: ~out,
					bodyindex: ~bodyindex
				);
			}, {
				~play = "You have to supply an instace of Cembalo".postln
			})
		}, (
			randomStrum: false,
			timbre: 0,
			bendDelay: 1,
			bendAm: 1,
			attack: 0,
			release: 0,
			out: nil,
			bodyindex: 0
		));
		"Done.".postln;

		"Setting up midi event type...".postln;
		Event.addEventType(\cembalomidi, {
			if(~cembalo.notNil, {
				~play = ~cembalo.playMIDINote(
					note: ~midinote.value,
					dur: ~sustain.value,
					strum: ~strum.value,
					pan: ~pan,
					timbre: ~timbre,
					randomStrum: ~randomStrum,
					bendDelay: ~bendDelay,
					bendAm: ~bendAm,
					attack: ~attack,
					release: ~release,
					out: ~out,
					bodyindex: ~bodyindex
				);
			}, {
				~play = "You have to supply an instace of Cembalo".postln
			})
		}, (
			randomStrum: false,
			timbre: 0,
			bendDelay: 1,
			bendAm: 1,
			attack: 0,
			release: 0,
			out: nil,
			bodyindex: nil
		));
		"Done.".postln;
	}

	// *** Instance method: generateFifthBasedScale
	generateFifthBasedScale {|fractionOfComma=0|
		var comma = 81/80;

		^this.generateScale(1.5 / comma.pow(fractionOfComma));
	}

	// *** Instance method: generateScale
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

	// *** Instance method: setRootFreq
	setRootFreq {|hertz, index = 9|
		var ratios, ratio;

		index = index ? 9;
		
		ratios = this.tuningAsArray;
		ratio = ratios[9] / ratios[index];
		rootFreq = hertz * ratio;
		this.adjustSampleOffset;
	}

	// *** Instance method: adjustSampleOffset
	adjustSampleOffset {
		var ratio = this.tuningAsArray()[9];
		var concertC = 440 / 9.midiratio;
		var diff, sampleOffset;
		var oldOffset;

		"Adjusting sample offset using A=%...\n".postf(rootFreq);
						
		masterRate = (rootFreq / ratio) / concertC;

		diff = masterRate.ratiomidi;
		sampleOffset = diff.floor.asInteger;

		masterRate = (diff - sampleOffset).midiratio;

		oldOffset = bufferIndexOffset;

		bufferIndexOffset = sampleOffset;

		sampleOffset = sampleOffset - oldOffset;

		// Function that iterates over all the buffers and offsets
		// them sampleOffset number of steps.
		if(sampleOffset > 0, {
			127.do{|index|
				buffers[index - sampleOffset] = buffers[index]
			}
		}, {
			(127..0).do{|index|
				buffers[index - sampleOffset] = buffers[index]
			}
		});
	}
	
	// *** Instance method: tuning_
	tuning_{|newTuning, rootFreq, rootFreqIndex|
		tuning = newTuning;
		this.tuningSetup(tuning);
		if(rootFreq.notNil, {
			this.setRootFreq(rootFreq, rootFreqIndex)
		})
	}

	// *** Instance method: tuningSetup
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
			};

		}, {
			if(tuning.isNumber, {
				var scale = this.generateFifthBasedScale(tuning);
				this.tuningSetup(scale);
			}, {
				switch(tuning,
					'et12', {rates = 1!12},
					'mean', {
						var scale = this.generateFifthBasedScale(1/4);
						"Loading quarter-comma meantone tuning...".postln;
						this.tuningSetup(scale);
					},
					'mean6', {
						var scale = this.generateFifthBasedScale(1/6);
						"Loading sixth-comma meantone tuning...".postln;
						this.tuningSetup(scale);
					},
					'pyth', {
						var scale = this.generateFifthBasedScale(0);
						"Loading pythagorean tuning...".postln;
						this.tuningSetup(scale)
					},
					'fivelimit', {
						"Loading five-limit tuning...".postln;
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
						"Loading seven-limit tuning...".postln;
						this.tuningSetup([
							1,
							16/15,
							9/8,
							7/6,
							5/4,
							4/3,
							7/5,
							3/2,
							14/9,
							5/3,
							7/4,
							15/8
						])
					},
					'w3', {
						"Loading werckminster III tuning...".postln;
						this.tuningSetup([
							1,
							256/243,
							(64/81) * 2.pow(1/2),
							32/27,
							(256/243) * 2.pow(1/4),
							4/3,
							1024/729,
							(8/9) * (2.pow(3)).pow(1/4),
							128/81,
							(1024/729) * 2.pow(1/4),
							16/9,
							(128/81) * 2.pow(1/4)
						])
					},
					{
						"Tuning % not found! Using default et12.\n".postf(tuning);
						rates = 1!12
					}
				)
			})
		});
		transposedRates = rates.rotate(root % 12);
		this.adjustSampleOffset();
	}

	// *** Instance method: tuningAsArray
	tuningAsArray {
		^transposedRates.collect{|item, index|
			index.midiratio * item
		};
	}

	// *** Instance method: getKeyFreq
	getKeyFreq {|key = 0|
		var octave = 2.pow(key.div(12) - 69.div(12));
		var ratios = this.tuningAsArray();
		var ratio = ratios[key % ratios.size] / ratios[9];

		^rootFreq * ratio * octave
	}

	// *** Instance method: arrayContains
	arrayContains {|array,value|
		var contains = false;
		array.asArray.do{|item|
			if(item == value, {contains = true})
		};

		^contains
	}

	// *** Instance method: findClosestKey
	findClosestKey {|key = 60|
		// initialize an index
		var i = 1;

		// if the key does in fact exist, return it
		if(keys[key].notNil, {
			^key
		});

		// as long as either one of the indexes we're looking at are
		// in the specified range, keep looking
		while({ (key - i >= 0) || (key + i < 128)}, {
			// if a key under the key supplied is not nil, return it
			if(keys[key - i].notNil, {
				^(key - i)
			}, {
				// same goes for a key over
				if(keys[key + i].notNil, {
					^(key + i)
				}, {
					// if not, search wider.
					i = i + 1;
				})
			})
		});
	}

	// *** Instance method: findClosestSample
	findClosestSample {|key = 60|
		var i = 1;

		if(buffers[key].notNil, {
			^key
		});

		while({ (key - i >= 0) || (key + i < 128)}, {

			if(buffers[key - i].notNil, {
				^(key - i)
			}, {
				if(buffers[key + i].notNil, {
					^(key + i)
				}, {
					i = i + 1
				})
			})
		})
	}
	
	// *** Instance method: loadSynthDefs
	loadSynthDefs {
		// *** SynthDef: cembalo_player
		SynthDef(\cembalo_player, {
			| buf = 0
			, gate = 1
			, outL = 0
			, outR = 1
			, rate = 1
			, pan = 0
			, amp = 1
			, atk = 0
			, rel = 0
			, lagTime = 0.1
			|

			var env = EnvGen.kr(Env.asr(atk,1,rel), gate, doneAction:2);
			var sig = PlayBuf.ar(2, buf, rate.lag(lagTime) * BufRateScale.kr(buf)) * env;

			sig = Balance2.ar(sig[0], sig[1], pan);

			sig = sig * amp;

			Out.ar(outL, sig[0]);
			Out.ar(outR, sig[1])
		}).add;

		// *** SynthDef: cembalo_player_oneshot
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

		// *** SynthDef: cembalo_player_mono
		SynthDef(\cembalo_player_mono_to_stereo, {
			| buf = 0
			, gate = 1
			, outL = 0
			, outR = 1
			, rate = 1
			, pan = 0
			, amp = 1
			, atk = 0
			, rel = 0
			, lagTime = 0.1
			|

			var env = EnvGen.kr(Env.asr(atk,1,rel), gate, doneAction:2);
			var sig = PlayBuf.ar(1, buf, rate.lag(lagTime) * BufRateScale.kr(buf)) * env;

			sig = Pan2.ar(sig, pan);

			sig = sig * amp;

			Out.ar(outL, sig[0]);
			Out.ar(outR, sig[1])
		}).add;

		// *** SynthDef: cembalo_player_oneshot
		SynthDef(\cembalo_player_oneshot_mono_to_stereo, {
			| buf = 0
			, outL = 0
			, outR = 1
			, rate = 1
			, pan = 0
			, amp 1
			|

			var sig = PlayBuf.ar(1, buf, rate, doneAction:2);
			sig = Pan2.ar(sig, pan);
			sig = sig * amp;

			Out.ar(outL, sig[0]);
			Out.ar(outR, sig[1]);
		}).add;

		// *** SynthDef: cembalo_player_mix_to_mono
		SynthDef(\cembalo_player_mix_to_mono, {
			| buf = 0
			, gate = 1
			, out = 0
			, rate = 1
			, pan = 0
			, amp = 1
			, atk = 0
			, rel = 0
			, lagTime = 0.1
			|

			var env = EnvGen.kr(Env.asr(atk,1,rel), gate, doneAction:2);
			var sig = PlayBuf.ar(2, buf, rate.lag(lagTime) * BufRateScale.kr(buf)) * env;
			
			sig = Mix(sig);

			sig = sig * amp;

			Out.ar(out, sig)
		}).add;

		// *** SynthDef: cembalo_player_oneshot_mix_to_mono
		SynthDef(\cembalo_player_oneshot_mix_to_mono, {
			| buf = 0
			, out = 0
			, rate = 1
			, pan = 0
			, amp 1
			|

			var sig = PlayBuf.ar(2, buf, rate, doneAction:2);
			
			sig = Mix(sig);
			sig = sig * amp;

			Out.ar(out, sig)
		}).add;

		// *** SynthDef: cembalo_player_mono
		SynthDef(\cembalo_player_mono, {
			| buf = 0
			, gate = 1
			, out = 0
			, rate = 1
			, pan = 0
			, amp = 1
			, atk = 0
			, rel = 0
			, lagTime = 0.1
			|

			var env = EnvGen.kr(Env.asr(atk,1,rel), gate, doneAction:2);
			var sig = PlayBuf.ar(1, buf, rate.lag(lagTime) * BufRateScale.kr(buf)) * env;

			sig = sig * amp;

			Out.ar(out, sig)
		}).add;

		// *** SynthDef: cembalo_player_oneshot_mono
		SynthDef(\cembalo_player_oneshot_mono, {
			| buf = 0
			, out = 0
			, rate = 1
			, pan = 0
			, amp 1
			|

			var sig = PlayBuf.ar(1, buf, rate, doneAction:2);
			
			sig = sig * amp;

			Out.ar(out, sig)
		}).add;
	}

	// *** Instance method: loadConfiguration
	loadConfiguration {
		if(userSamplePath.notNil, {
			configurationPath = userSamplePath ++ "info.json"
		}, {
			configurationPath = path ++ "samples/info.json"
		});

		if(File.exists(configurationPath), {
			var file = File.new(configurationPath, "r").readAllString;
			configuration = file.parseJSON;
		}, {
			var file;
			configurationPath = path ++ "samples/info.json";

			file = File.new(configurationPath, "r").readAllString;
			configuration = file.parseJSON;
		});
	}

	// *** Instance method: loadBuffers
	loadBuffers {
		var bodyPath, releasePath;

		if(userSamplePath.notNil, {
			"using supplied path %\n".postf(userSamplePath);
			bodyPath = userSamplePath ++ "bod/*.wav";
			releasePath = userSamplePath ++ "rel/*.wav";
		}, {
			bodyPath = path ++ "samples/bod/*.wav";
			releasePath = path ++ "samples/rel/*.wav";
		});

		bodyPath.pathMatch.do{|filePath|
			var num = filePath.findRegexp(".{3}(?=.wav)")[0][1].compile.value();
			var buffer = Buffer.read(server, filePath);

			if(buffers[num] == nil, {
				buffers[num] = Dictionary.newFrom([\nn, num, \body, [buffer], \centOffset, 0]);
			}, {
				buffers[num][\body] = buffers[num][\body] ++ buffer
			});
		};

		releasePath.pathMatch.do{|filePath|
			var num = filePath.findRegexp(".{3}(?=.wav)")[0][1].compile.value();
			var buffer = Buffer.read(server, filePath);

			buffers[num][\release] = buffer;
		};
	}

	// *** Instance method: getNumBuffers
	getNumBuffers {
		var num = 0;
		buffers.do{|item|
			if(item.notNil, {
				num = num + item[\body].size;
				if(item[\release].notNil, {
					num = num + 1
				})
			})
		};

		^num;
	}
}
// Local Variables:
// eval: (outshine-mode 1)
// End: