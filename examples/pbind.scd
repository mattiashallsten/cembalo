// define an instance of `Cembalo'
c = Cembalo()

// `Pbind' using the custom event type `\cembalo':
(
p = Pbind(
	\type, \cembalo,
	\cembalo, c,
	\freq, 100 *
	Prand([1,5/4,3/2],inf) *
	Prand([1,3/2,2],inf) *
	Prand([1, [0.5,1], [1,3/2]],inf),
	\dur, Pwhite(2.0,4.0),
	\legato, Pwhite(1.0,4.0)
).play
)