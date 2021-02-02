CembaloKey {
	var <nn, out, outL, outR, amp, pan, <bodyBuffer, <releaseBuffer, bodySynthdef, releaseSynthdef;
	var <player, playerTimer, keyIsDepressed = false, rate = 1, bendAm = 0;
	var bodyLength;

	// * Class method: *new
	*new {
		| nn = 24
		, out = 0
		, outL = 0
		, outR = 1
		, amp = 0.7
		, pan = 0
		, bodyBuffer = 0
		, releaseBuffer = 0
		, bodySynthdef = nil
		, releaseSynthdef = nil
		|

		^super.newCopyArgs(
			nn,
			out,
			outL,
			outR,
			amp,
			pan,
			bodyBuffer,
			releaseBuffer,
			bodySynthdef,
			releaseSynthdef
		).initCembaloKey;		
	}

	initCembaloKey {
		bodyLength = bodyBuffer.numFrames / bodyBuffer.sampleRate;
	}

	// * Instance method: keyOn
	keyOn {|newRate, newAmp, newPan|

		if(newRate.notNil, {
			rate = newRate
		});
		if(newAmp.notNil, {
			amp = newAmp
		});
		if(newPan.notNil, {
			pan = newPan
		});
		
		if(keyIsDepressed, {
			this.keyOff;
		});

		//		"turning on key %".format(nn).postln;
		player = Synth(bodySynthdef, [
			\buf, bodyBuffer,
			\out, out,
			\outL, outL,
			\outR, outR,
			\rate, rate * bendAm.midiratio,
			\pan, pan,
			\amp, amp
		]);
		
		playerTimer = fork { wait(bodyLength - 0.1); player.set(\gate, 0); player = nil };
		
		keyIsDepressed = true;
	}
	
	// * Instance method: keyOff
	keyOff {
		if(keyIsDepressed, {
			//"turning off key %".format(nn).postln;

			if(playerTimer.notNil, { playerTimer.stop } );
			
			if(player.notNil, {
				player.set(\gate, 0);
				player = nil;
			});

			Synth(releaseSynthdef, [
				\buf, releaseBuffer,
				\out, out,
				\outL, outL,
				\outR, outR,
				\rate, rate,
				\pan, pan,
				\amp, amp
			]);

			keyIsDepressed = false
		});

		this.bend(0);
	}

	bend {|val|
		bendAm = val;
		if(player.notNil, {
			player.set(\rate, rate * bendAm.midiratio)
		})
	}

	// * Instance method: rate_
	rate_ {|val|
		rate = val;
	}

	// * Instance method: amp_
	amp_ {|val|
		out = val
	}
}
// Local Variables:
// eval: (outshine-mode 1)
// End: