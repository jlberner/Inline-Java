use strict ;
use Test::More tests => 37;

use Inline (
	Java => 'DATA',
	STUDY => ['org.perl.inline.java.InlineJavaPerlCaller'],
	STARTUP_DELAY => 20,	
	EXTRA_JAVA_ARGS => '-Xmx256m',
) ;

use Inline::Java qw(cast caught) ;
use Data::Dumper;

my $mtc_cnt = 0 ;
my $mtc_mode = 0 ;
my $t = new t15() ;

{
	eval {
		is($t->add(5, 6), 11) ;
		is($t->add_via_perl(5, 6), 11) ;
		my $a = $t->incr_via_perl([7, 6, 5]) ;
		is($a->[1], 7) ;
		$a = $t->incr_via_perl_ctx($a) ;
		is($a->[1], 8) ;
		is($t->mul(5, 6), 30) ;
		is($t->mul_via_perl(5, 6), 30) ;
		is($t->silly_mul(3, 2), 6) ;
		is($t->silly_mul_via_perl(3, 2), 6) ;

		is(add_via_java(3, 4), 7) ;

		is($t->add_via_perl_via_java(3, 4), 7) ;
		is($t->silly_mul_via_perl_via_java(10, 9), 90) ;

		is(t15->add_via_perl_via_java_t($t, 6, 9), 15) ;

		is($t->cat_via_perl("Inline", "Java"), "InlineJava") ;

		is($t->perl_static(), 'main->static') ;

		is(twister(20, 0, 0), "return perl twister") ;
		is($t->twister(20, 0, 0), "return java twister") ;

		eval {twister(20, 0, 1)} ; like($@, qr/^throw perl twister/) ;
				
		my $msg = '' ;
		eval {$t->twister(20, 0, 1)} ;
		if ($@) {
			if (caught('t15$OwnException')){
				$msg = $@->getMessage() ;
			}
			else{
				$msg = $@ ;
			}
		}
		is($msg, "throw java twister") or diag Dumper $msg;

		eval {$t->bug()} ; like($@, qr/^bug/) ;

		is(cast('t15', $t->perlt())->add(5, 6), 11) ;

		eval {$t->perldummy()} ; like($@, qr/Can't propagate non-/) ; #'

		$t->mtc_callbacks(20) ;
		$t->StartCallbackLoop() ;
		is($mtc_cnt, 20) ;

		$mtc_cnt = -30 ;
		$t->mtc_callbacks2(50) ;
		$t->StartCallbackLoop() ;
		is($mtc_cnt, 20) ;

		$mtc_cnt = 0 ;
		$mtc_mode = 1 ;
		$t->mtc_callbacks2(20) ;
		$t->StartCallbackLoop() ;
		is($mtc_cnt, 20) ;

		$mtc_cnt = 0 ;
		$mtc_mode = 2 ;
		$t->mtc_callbacks2(20) ;
		$t->OpenCallbackStream() ;
		while (($mtc_cnt < 20)&&($t->WaitForCallback(-1) > 0)){
			$t->ProcessNextCallback() ;
		}
		is($mtc_cnt, 20) ;

		$mtc_cnt = 0 ;
		$mtc_mode = 2 ;
		$t->mtc_callbacks2(10) ;
		while ($t->WaitForCallback(3.1416) > 0){
			cmp_ok($t->WaitForCallback(0), '>=', 1) ;
			$t->ProcessNextCallback() ;
		}
		is($mtc_cnt, 10) ;

		# Unfortunately we can't test this because the Thread.run method doesn't allow us
		# to throw any exceptions...
		# $t->mtc_callbacks_error() ;
	} ;
	if ($@){
		if (caught("java.lang.Throwable")){
			$@->printStackTrace() ;
			die("Caught Java Exception") ;
		}
		else{
			die $@ ;
		}
	}
}

is($t->__get_private()->{proto}->ObjectCount(), 1) ;


sub add {
	my $i = shift ;
	my $j = shift ;

	return $i + $j ;
}


sub incr {
	my $ija = shift ;
	
	for (my $i = 0 ; $i < $ija->length() ; $i++){
		$ija->[$i]++ ;
	}

	return wantarray ? @{$ija} : $ija ;
}


sub mul {
	my $i = shift ;
	my $j = shift ;

	return $i * $j ;
}


sub cat {
	my $i = shift ;
	my $j = shift ;

	return $i . $j ;
}


sub add_via_java {
	my $i = shift ;
	my $j = shift ;

	return $t->add($i, $j) ;
}


sub add_via_java_t {
	my $_t = shift ;
	my $i = shift ;
	my $j = shift ;

	return $_t->add($i, $j) ;
}


sub twister {
	my $max = shift ;
	my $cnt = shift ;
	my $explode = shift ;

	if ($cnt == $max){
		if ($explode){
			die("throw perl twister") ;
		}
		else{
			return "return perl twister" ;
		}
	}
	else{
		return $t->twister($max, $cnt+1, $explode) ;
	}
}


sub t {
	return $t ;
}


sub dummy {
	die(bless({}, "Inline::Java::dummy")) ;
}



sub mt_callback {
	my $pc = shift ;
	$mtc_cnt++ ;
	if ($mtc_cnt >= 20){
		if ($mtc_mode == 0){
			$pc->StopCallbackLoop() ;
		}
		elsif ($mtc_mode == 1){
			my $o = new org::perl::inline::java::InlineJavaPerlCaller() ;
			$o->StopCallbackLoop() ;
		}
	}	
}


sub static_method {
	my $class = shift ;

	return 'main->static' ;
}


__END__

__Java__


import java.io.* ;
import org.perl.inline.java.* ;

class t15 extends InlineJavaPerlCaller {
	class OwnException extends Exception {
		OwnException(String msg){
			super(msg) ;
		}
	}

	class OwnThread extends Thread {
		InlineJavaPerlCaller pc = null ;
		boolean error = false ;

		OwnThread(InlineJavaPerlCaller _pc, int nb, boolean err){
			super("CALLBACK-TEST-THREAD-#" + nb) ;
			pc = _pc ;
			error = err ;
		}

		public void run(){
			try {
				if (! error){
					pc.CallPerlSub("main::mt_callback", new Object [] {pc}) ;
				}
				else {
					new InlineJavaPerlCaller() ;
				}
			}
			catch (InlineJavaException ie){
				ie.printStackTrace() ;
			}
			catch (InlineJavaPerlException ipe){
				ipe.printStackTrace() ;
			}
		}
	}

	public t15() throws InlineJavaException {
	}

	public int add(int a, int b){
		return a + b ;
	}

	public int mul(int a, int b){
		return a * b ;
	}

	public int silly_mul(int a, int b){
		int ret = 0 ;
		for (int i = 0 ; i < b ; i++){
			ret = add(ret, a) ;
		}
		return a * b ;
	}

	public int silly_mul_via_perl(int a, int b) throws InlineJavaException, InlineJavaPerlException {
		int ret = 0 ;
		for (int i = 0 ; i < b ; i++){
			ret = add_via_perl(ret, a) ;
		}
		return ret ;
	}

	public int add_via_perl(int a, int b) throws InlineJavaException, InlineJavaPerlException {
		String val = (String)CallPerlSub("main::add", 
			new Object [] {Integer.valueOf(a), Integer.valueOf(b)}) ;

		return Integer.valueOf(val).intValue() ;
	}

	public int [] incr_via_perl(int a[]) throws InlineJavaException, InlineJavaPerlException {
		int [] r = (int [])CallPerlSub("main::incr", 
			new Object [] {a}, a.getClass()) ;

		return r ;
	}

	public int [] incr_via_perl_ctx(int a[]) throws InlineJavaException, InlineJavaPerlException {
		int [] r = (int [])CallPerlSub("@main::incr", 
			new Object [] {a}, a.getClass()) ;

		return r ;
	}

	public void death_via_perl() throws InlineJavaException, InlineJavaPerlException {
		InlineJavaPerlCaller c = new InlineJavaPerlCaller() ;
		c.CallPerlSub("main::death", null) ;
	}

	public void except() throws InlineJavaException, InlineJavaPerlException {
		throw new InlineJavaPerlException("test") ;
	}

	public int mul_via_perl(int a, int b) throws InlineJavaException, InlineJavaPerlException {
		String val = (String)CallPerlSub("main::mul", 
			new Object [] {Integer.valueOf(a), Integer.valueOf(b)}) ;

		return Integer.parseInt(val) ;
	}

	public int add_via_perl_via_java(int a, int b) throws InlineJavaException, InlineJavaPerlException {
		String val = (String)CallPerlSub("main::add_via_java", 
			new Object [] {Integer.valueOf(a), Integer.valueOf(b)}) ;

		return Integer.parseInt(val);
	}

	static public int add_via_perl_via_java_t(t15 t, int a, int b) throws InlineJavaException, InlineJavaPerlException {
		InlineJavaPerlCaller c = new InlineJavaPerlCaller() ;
		String val = (String)c.CallPerlSub("main::add_via_java_t", 
			new Object [] {t, Integer.valueOf(a), Integer.valueOf(b)}) ;

		return Integer.parseInt(val);
	}


	public int silly_mul_via_perl_via_java(int a, int b) throws InlineJavaException, InlineJavaPerlException {
		int ret = 0 ;
		for (int i = 0 ; i < b ; i++){
			String val = (String)CallPerlSub("add_via_java", 
				new Object [] {Integer.valueOf(ret), Integer.valueOf(a)}) ;
			ret = Integer.parseInt(val);
		}
		return ret ;
	}


	public String cat_via_perl(String a, String b) throws InlineJavaException, InlineJavaPerlException {
		String val = (String)CallPerlSub("cat", 
			new Object [] {a, b}) ;

		return val ;
	}

	public String twister(int max, int cnt, int explode) throws InlineJavaException, InlineJavaPerlException, OwnException {
		if (cnt == max){
			if (explode > 0){
				throw new OwnException("throw java twister") ;
			}
			else{
				return "return java twister" ;
			}
		}
		else{
			return (String)CallPerlSub("twister", 
				new Object [] {Integer.valueOf(max), Integer.valueOf(cnt+1), Integer.valueOf(explode)}) ;
		}
	}


	public void bug() throws InlineJavaException {
		throw new InlineJavaException("bug") ;
	}


	public Object perlt() throws InlineJavaException, InlineJavaPerlException, OwnException {
		return CallPerlSub("t", null) ;
	}


	public Object perl_static() throws InlineJavaException, InlineJavaPerlException, OwnException {
		return CallPerlStaticMethod("main", "static_method", null) ;
	}


	public Object perldummy() throws InlineJavaException, InlineJavaPerlException, OwnException {
		return CallPerlSub("dummy", null) ;
	}

	public void mtc_callbacks(int n){
		for (int i = 0 ; i < n ; i++){
			OwnThread t = new OwnThread(this, i, false) ;
			t.start() ;
		}
	}

	public void mtc_callbacks2(int n) throws InlineJavaException, InlineJavaPerlException {
		for (int i = 0 ; i < n ; i++){
			InlineJavaPerlCaller pc = new InlineJavaPerlCaller() ;
			OwnThread t = new OwnThread(pc, i, false) ;
			t.start() ;
		}
	}

	public void mtc_callbacks_error(){
		OwnThread t = new OwnThread(this, 0, true) ;
		t.start() ;
	}
}
