CembaloKey {
	var <nn, <out, outL, outR, amp, pan, attack, release, <bodyBuffer, <releaseBuffer, sampleAdjust, parent;
	var <player, playerTimer, keyIsDepressed = false, sustainPedal = false;
	var rate = 1, bendAm = 1, timbre = 0, compRate = 1;
	var bodyLength;

	// * Class method: *new
	*new {
		| nn = 24
		, out = 0
		, outL = 0
		, outR = 1
		, amp = 0.7
		, pan = 0
		, attack = 0
		, release = 0
		, bodyBuffer = 0
		, releaseBuffer = 0
		, sampleAdjust = 1
		, parent = nil
		|

		^super.newCopyArgs(
			nn,
			out,
			outL,
			outR,
			amp,
			pan,
			attack,
			release,
			bodyBuffer,
			releaseBuffer,
			sampleAdjust,
			parent
		).initCembaloKey;		
	}

	// *** Instance method: initCembaloKey
	initCembaloKey {
		bodyLength = (((bodyBuffer.numFrames / bodyBuffer.sampleRate) / rate) / compRate);
	}

	// * Instance method: keyOn
	keyOn {|newRate, newAmp, newPan, newTimbre = 0, newAttack, newRelease|

		// if(newRate.notNil, {
		// 	rate = newRate
		// });
		// if(newAmp.notNil, {
		// 	amp = newAmp
		// });
		// if(newPan.notNil, {
		// 	pan = newPan
		// });
		// if(newAttack.notNil, {
		// 	attack = newAttack
		// });
		// if(newRelease.notNil, {
		// 	release = newRelease
		// });

		rate = newRate ? rate;
		amp = newAmp ? amp;
		pan = newPan ? pan;
		timbre = newTimbre;
		attack = newAttack ? attack;
		release = newRelease ? release;
		
		if(keyIsDepressed, {
			this.keyOff;
		});

		// make adjustments in playback rate (set value of compRate)
		this.adjustRate;
		
		
		//		"turning on key %".format(nn).postln;
		if(bodyBuffer.numChannels == 2, {
			player = Synth(parent.bodySynthdef, [
				\buf, bodyBuffer,
				\out, out,
				\outL, outL,
				\outR, outR,
				\rate, rate * bendAm * compRate * sampleAdjust,
				\pan, pan,
				\atk, attack,
				\rel, release,
				\amp, amp
			]);
		}, {
			player = Synth(parent.bodySynthdefMono, [
				\buf, bodyBuffer,
				\out, out,
				\outL, outL,
				\outR, outR,
				\rate, rate * bendAm * compRate * sampleAdjust,
				\pan, pan,
				\atk, attack,
				\rel, release,
				\amp, amp
			]);
		});
			
		
		// playerTimer = fork { wait(bodyLength - 0.1); player.set(\gate, 0); player = nil };
		
		keyIsDepressed = true;
	}
	
	// * Instance method: keyOff
	keyOff {
		if(keyIsDepressed, {
			if(playerTimer.notNil, { playerTimer.stop } );
			
			if(player.notNil, {
				player.set(\gate, 0);
				player = nil;
			});

			if((releaseBuffer.notNil) && (release == 0), {
				if(releaseBuffer.numChannels == 2, {
					Synth(parent.releaseSynthdef, [
						\buf, releaseBuffer,
						\out, out,
						\outL, outL,
						\outR, outR,
						\rate, rate * bendAm * compRate * sampleAdjust,
						\pan, pan,
						\amp, amp
					]);
				}, {
					Synth(parent.releaseSynthdefMono, [
						\buf, releaseBuffer,
						\out, out,
						\outL, outL,
						\outR, outR,
						\rate, rate * bendAm * compRate * sampleAdjust,
						\pan, pan,
						\amp, amp
					]);
				});
			});
		});

		this.bend(1);
	}	
	
	// * Instance method: adjustRate
	adjustRate {
		var sampleindex, adjusted_rate, buffer_cent_offset;
		// remap timbre value to -32 <-> 32. `sampleindex' will be the
		// sample to use for playback.
		sampleindex = (timbre * (-32)).asInteger;
		// add remapped value to nn -- get the sample to play
		//sampleindex = (nn + sampleindex).clip(parent.midiNoteOffset, parent.midiNoteCeil);

		// use the method `findClosestKey' to find the key to map to
		sampleindex = parent.findClosestSample(nn + sampleindex);
		// get ratio between new index and target pitch
		adjusted_rate = (nn - sampleindex).midiratio;

		// find a new buffer to use, at the index `sampleindex'.
		bodyBuffer = parent.buffers[sampleindex][\body];

		if(releaseBuffer.notNil,{
			releaseBuffer = parent.buffers[sampleindex][\release];
		});

		// apply offset that's inherent in the buffer:
		if(parent.buffers[sampleindex][\centOffset].notNil, {
			buffer_cent_offset = parent.buffers[sampleindex][\centOffset] / 100
		}, {
			buffer_cent_offset = 0
		});

		compRate = adjusted_rate * (buffer_cent_offset * (-1)).midiratio;
		this.initCembaloKey()
	}

	// *** Instance method: bend
	bend {|val|
		bendAm = val;
		if(player.notNil, {
			player.set(\rate, rate * bendAm * compRate * sampleAdjust)
		})
	}

	// * Instance method: rate_
	rate_ {|val|
		rate = val;
	}

	// * Instance method: amp_
	amp_ {|val|
		amp = val
	}

	out_ {|val|
		out = val.div(1);
		outL = val.div(1);
		outR = val.div(1) + 1;
	}

	// *** Instance method: attack_
	attack_ {|val|
		attack = val
	}

	// *** Instance method: release_
	release_ {|val|
		release = val
	}
}
// Local Variables:
// eval: (outshine-mode 1)
// End: