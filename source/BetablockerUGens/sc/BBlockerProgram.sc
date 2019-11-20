BBlockerProgram {
	classvar <instructions, descs, <addrRange = 256;
	var <program, <>fillUpRandom = false;
	var collection;

	*new {|program|
		^super.new.init(program)
	}


	init {|argProgram|
		this.class.initStates;

		program = argProgram.asArray;
	}

	// search in descs
	*find{|identifier|
		this.initStates;

		identifier = identifier.asString.toLower;
		^descs.select{|value, key|
			key.asString.toLower.contains(identifier) ||{value.toLower.contains(identifier)}}.indices
	}

	*descOf{|key|
		^this.descs[key]
	}

	*printDescs {
		this.descs.keysValuesDo{|key, val|
			"%: %\n".postf((key.asString ++ "    ")[0..3], val)
		}
	}

	*descs {
		this.initStates;
		^descs;
	}

	compile {
		^program.collect{|instr| this.pr_translate(instr)};
	}
	pr_fill {|data|
		^(data ++ fillUpRandom.if({{addrRange.rand}!(addrRange - data.size)}, {0!(addrRange - data.size)}))
	}
	collection {|force = false|
		var result;

		force.if{
			collection = nil;
		};

		collection.isNil.if{
			collection = this.pr_fill(this.compile);
		}

		^collection;
	}

	makeBuffer {|server, action, force = false|
		server = server ?? {Server.default};
		^Buffer.sendCollection(server, this.collection(false), 1, action: action)
	}

	asLocalBuf {|force = false|
		^LocalBuf.newFrom(this.collection(false))
	}

	fillBuffer {|buffer, force = false|
		buffer.sendCollection(this.collection(force));
	}

	decompile {|force = false|
		this.collection(force);
		^this.class.new(collection.collect{|val| this.pr_rTranslate(val)})
	}

	pr_translate {|val|
		^(this.class.instructions[val] ?? {val%addrRange})
	}

	pr_rTranslate {|val|
		^(this.class.instructions.findKeyForValue(val) ? val)
	}

	*initStates {
		instructions = instructions ?? {
			(
				\NOP :  0,
				\ORG :  1,
				\EQU :  2,
				\JMP :  3,
				\JMPZ:  4,
				\PSHL:  5,
				\PSH :  6,
				\PSHI:  7,
				\POP :  8,
				\POPI:  9,
				\ADD : 10,
				\SUB : 11,
				\INC : 12,
				\DEC : 13,
				\AND : 14,
				\OR  : 15,
				\XOR : 16,
				\NOT : 17,
				\ROR : 18,
				\ROL : 19,
				\PIP : 20,
				\PDP : 21,
				\DUP : 22,
				\NOTE: 23,
				\VOX : 24,
				\STOP: 25
			)
		};

		descs.isNil.if({
			descs = Order();
			descs.put('NOP' , "do nothing");
			descs.put('ORG' , "define relative origin address");
			descs.put('EQU' , "compare two topmost elements on stack, pop them, push result (0 or 1)");
			descs.put('JMP' , "jump to address specified in following slot");
			descs.put('PSH' , "push value of slot specified by address specified by address in following slot");
			descs.put('POP' , "pop item from stack to slot specified by address in following slot");
			descs.put('ADD' , "perform addition (+) on first two elements on stack, pop them, push result");
			descs.put('SUB' , "perform subtraction (-) on first two elements on the stack, pop them, push result");
			descs.put('INC' , "increment value on stack");
			descs.put('DEC' , "decrement value on stack");
			descs.put('AND' , "perform bit-wise AND on first two elements on stack, pop them, push result");
			descs.put('XOR' , "perform bit-wise XOR on first two elements on stack, pop them, push result");
			descs.put('NOT' , "perform bit-wise NOT on first element on stack, pop it, push result");
			descs.put('ROR' , "perform right shift  on first two elements on stack, pop them, push result");
			descs.put('ROL' , "perform left shift   on first two elements on stack, pop them, push result");
			descs.put('PIP' , "increment value specified by address in following slot (no stack involved)");
			descs.put('PDP' , "decrement value specified by address in following slot (no stack involved)");
			descs.put('DUP' , "push a copy (duplicate) of topmost value to the stack");
			descs.put('VOX' , "play vox  (here: NOP)");
			descs.put('JMPZ', "if stack returns 0, jump to address specified in following slot");
			descs.put('PSHL', "push value of slot specified by adress in following slot");
			descs.put('PSHI', "push value like PSH but one more encapsulation");
			descs.put('POPI', "pop item from stack to slot specified by address specified by address in following slot");
			descs.put('NOTE', "play note (here: NOP)");
			descs.put('STOP', "stop program (here: NOP)");
			descs.put('OR'  ,  "perform bit-wise  OR on first two elements on stack, pop them, push result");
		});

	}

	ar {|rate = 20000, using, leak = false, vol = 1, force = false| // using is either Server instance or Buffer instance
		var buffer, server;

		using.isKindOf(Buffer).if({
			buffer = using;
			server = buffer.server;

			// force re-building
			force.if{
				this.collection(true);
			};
			this.fillBuffer(buffer);
		}, {
			server = using ? Server.default;
			buffer = this.collection(force);
		});

		^{
			var bufnum = buffer.isKindOf(Buffer).if({
				buffer.bufnum;
			}, {
				buffer.as(LocalBuf)
			});

			var src = Demand.ar(
				Impulse.ar(rate),
				0,
				DetaBlockerBuf(bufnum, 0)
			);

			leak.if{
				src = LeakDC.ar(src);
			};

			src * vol;

		}
	}

	play {|rate = 20000, using, leak = false, vol = 0.1, force = true| // using is either Server or Buffer
		^this.ar(rate, using, leak, vol, force).play
	}

	plot {|rate = 20000, using, leak = false, duration = 1, force = true| // using is either Server or Buffer
		^this.ar(rate, using, leak, force: force).plot(duration)
	}

	scope {|rate = 20000, using, leak = false, vol = 0.1, force = true| // using is either Server or Buffer
		^this.ar(rate, using, leak, vol, force).scope
	}

	storeOn { | stream |
		stream << this.class.name << "(" << this.program.asCompileString << ")" << ".fillUpRandom_(" << this.fillUpRandom << ")" ;
	}
}



