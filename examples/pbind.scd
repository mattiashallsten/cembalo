// define an instance of `Cembalo'
c = Cembalo()

// `Pbindef' using the custom event type `\cembalo':
(
Pbindef(\cembalo,
	\type, \cembalo,
	\cembalo, c,
	\freq, 75 *
	Prand([
		1,
		5/4,
		3/2,
		7/4
	],inf) *
	Prand([1,3/2,2],inf) *
	Prand([
		1,
		[0.5,1],
		[1,3/2],
		[1,3/2,2],
		[1,3/2,9/4]
	],inf),
	\strum, Pwhite(0.1,0.3),
	\randomStrum, true,
	\panDispersion, 0.8,
	\dur, Pwhite(0.6,2.0),
	\legato, 4
).play
)

Pbindef(\cembalo).stop;