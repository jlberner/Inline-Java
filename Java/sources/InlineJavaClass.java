import java.util.* ;


class InlineJavaClass {
	private InlineJavaServer ijs ;
	private InlineJavaProtocol ijp ;


	InlineJavaClass(InlineJavaServer _ijs, InlineJavaProtocol _ijp){
		ijs = _ijs ;
		ijp = _ijp ;
	}


	/*
		Makes sure a class exists
	*/
	Class ValidateClass(String name) throws InlineJavaException {
		Class pc = FindType(name) ;
		if (pc != null){
			return pc ;
		}

		try {
			Class c = Class.forName(name) ;
			return c ;
		}
		catch (ClassNotFoundException e){
			throw new InlineJavaException("Class " + name + " not found") ;
		}
	}

	/*
		This is the monster method that determines how to cast arguments
	*/
	Object [] CastArguments (Class [] params, ArrayList args) throws InlineJavaException {
		Object ret[] = new Object [params.length] ;
	
		for (int i = 0 ; i < params.length ; i++){	
			// Here the args are all strings or objects (or undef)
			// we need to match them to the prototype.
			Class p = params[i] ;
			InlineJavaUtils.debug(4, "arg " + String.valueOf(i) + " of signature is " + p.getName()) ;

			ret[i] = CastArgument(p, (String)args.get(i)) ;
		}

		return ret ;
	}


	/*
		This is the monster method that determines how to cast arguments
	*/
	Object CastArgument (Class p, String argument) throws InlineJavaException {
		Object ret = null ;
	
		ArrayList tokens = new ArrayList() ;
		StringTokenizer st = new StringTokenizer(argument, ":") ;
		for (int j = 0 ; st.hasMoreTokens() ; j++){
			tokens.add(j, st.nextToken()) ;
		}
		if (tokens.size() == 1){
			tokens.add(1, "") ;
		}
		String type = (String)tokens.get(0) ;
		
		// We need to separate the primitive types from the 
		// reference types.
		boolean num = ClassIsNumeric(p) ;
		if ((num)||(ClassIsString(p))){
			Class ap = p ;
			if (ap == java.lang.Number.class){
				InlineJavaUtils.debug(4, "specializing java.lang.Number to java.lang.Double") ;
				ap = java.lang.Double.class ;
			}

			if (type.equals("undef")){
				if (num){
					InlineJavaUtils.debug(4, "args is undef -> forcing to " + ap.getName() + " 0") ;
					ret = ijp.CreateObject(ap, new Object [] {"0"}, new Class [] {String.class}) ;
					InlineJavaUtils.debug(4, " result is " + ret.toString()) ;
				}
				else{
					ret = null ;
					InlineJavaUtils.debug(4, "args is undef -> forcing to " + ap.getName() + " " + ret) ;
					InlineJavaUtils.debug(4, " result is " + ret) ;
				}
			}
			else if (type.equals("scalar")){
				String arg = ijp.Decode((String)tokens.get(1)) ;
				InlineJavaUtils.debug(4, "args is scalar -> forcing to " + ap.getName()) ;
				try	{
					ret = ijp.CreateObject(ap, new Object [] {arg}, new Class [] {String.class}) ;
					InlineJavaUtils.debug(4, " result is " + ret.toString()) ;
				}
				catch (NumberFormatException e){
					throw new InlineJavaCastException("Can't convert " + arg + " to " + ap.getName()) ;
				}
			}
			else{
				throw new InlineJavaCastException("Can't convert reference to " + p.getName()) ;
			}
		}
		else if (ClassIsBool(p)){
			if (type.equals("undef")){
				InlineJavaUtils.debug(4, "args is undef -> forcing to bool false") ;
				ret = new Boolean("false") ;
				InlineJavaUtils.debug(4, " result is " + ret.toString()) ;
			}
			else if (type.equals("scalar")){
				String arg = ijp.Decode(((String)tokens.get(1)).toLowerCase()) ;
				InlineJavaUtils.debug(4, "args is scalar -> forcing to bool") ;
				if ((arg.equals(""))||(arg.equals("0"))){
					arg = "false" ;
				}
				else{
					arg = "true" ;
				}
				ret = new Boolean(arg) ;
				InlineJavaUtils.debug(4, " result is " + ret.toString()) ;
			}
			else{
				throw new InlineJavaCastException("Can't convert reference to " + p.getName()) ;
			}
		}
		else if (ClassIsChar(p)){
			if (type.equals("undef")){
				InlineJavaUtils.debug(4, "args is undef -> forcing to char '\0'") ;
				ret = new Character('\0') ;
				InlineJavaUtils.debug(4, " result is " + ret.toString()) ;
			}
			else if (type.equals("scalar")){
				String arg = ijp.Decode((String)tokens.get(1)) ;
				InlineJavaUtils.debug(4, "args is scalar -> forcing to char") ;
				char c = '\0' ;
				if (arg.length() == 1){
					c = arg.toCharArray()[0] ;
				}
				else if (arg.length() > 1){
					throw new InlineJavaCastException("Can't convert " + arg + " to " + p.getName()) ;
				}
				ret = new Character(c) ;
				InlineJavaUtils.debug(4, " result is " + ret.toString()) ;
			}
			else{
				throw new InlineJavaCastException("Can't convert reference to " + p.getName()) ;
			}
		}
		else {
			InlineJavaUtils.debug(4, "class " + p.getName() + " is reference") ;
			// We know that what we expect here is a real object
			if (type.equals("undef")){
				InlineJavaUtils.debug(4, "args is undef -> forcing to null") ;
				ret = null ;
			}
			else if (type.equals("scalar")){
				// Here if we need a java.lang.Object.class, it's probably
				// because we can store anything, so we use a String object.
				if (p == java.lang.Object.class){
					String arg = ijp.Decode((String)tokens.get(1)) ;
					ret = arg ;
				}
				else{
					throw new InlineJavaCastException("Can't convert primitive type to " + p.getName()) ;
				}
			}
			else{
				// We need an object and we got an object...
				InlineJavaUtils.debug(4, "class " + p.getName() + " is reference") ;

				String c_name = (String)tokens.get(1) ;
				String objid = (String)tokens.get(2) ;

				Class c = ValidateClass(c_name) ;

				if (DoesExtend(c, p) > -1){
					InlineJavaUtils.debug(4, " " + c.getName() + " is a kind of " + p.getName()) ;
					// get the object from the hash table
					int id = Integer.parseInt(objid) ;
					Object o = ijs.GetObject(id) ;
					ret = o ;
				}
				else{
					throw new InlineJavaCastException("Can't cast a " + c.getName() + " to a " + p.getName()) ;
				}
			}
		}

		return ret ;
	}


	/* 
		Returns the number of levels that separate a from b
	*/
	int DoesExtend(Class a, Class b){
		return DoesExtend(a, b, 0) ;
	}


	int DoesExtend(Class a, Class b, int level){
		InlineJavaUtils.debug(4, "checking if " + a.getName() + " extends " + b.getName()) ;

		if (a == b){
			return level ;
		}

		Class parent = a.getSuperclass() ;
		if (parent != null){
			InlineJavaUtils.debug(4, " parent is " + parent.getName()) ;
			int ret = DoesExtend(parent, b, level + 1) ;
			if (ret != -1){
				return ret ;
			}
		}

		// Maybe b is an interface a implements it?
		Class inter[] = a.getInterfaces() ;
		for (int i = 0 ; i < inter.length ; i++){
			InlineJavaUtils.debug(4, " interface is " + inter[i].getName()) ;
			int ret = DoesExtend(inter[i], b, level + 1) ;
			if (ret != -1){
				return ret ;
			}
		}

		return -1 ;
	}


	/*
		Finds the wrapper class for the passed primitive type.
	*/
	Class FindWrapper (Class p){
		Class [] list = {
			byte.class,
			short.class,
			int.class,
			long.class,
			float.class,
			double.class,
			boolean.class,
			char.class,
		} ;
		Class [] listw = {
			java.lang.Byte.class,
			java.lang.Short.class,
			java.lang.Integer.class,
			java.lang.Long.class,
			java.lang.Float.class,
			java.lang.Double.class,
			java.lang.Boolean.class,
			java.lang.Character.class,
		} ;

		for (int i = 0 ; i < list.length ; i++){
			if (p == list[i]){
				return listw[i] ;
			}
		}

		return p ;
	}


	/*
		Finds the primitive type class for the passed primitive type name.
	*/
	Class FindType (String name){
		String [] list = {
			"byte",
			"short",
			"int",
			"long",
			"float",
			"double",
			"boolean",
			"char",
			"B",
			"S",
			"I",
			"J",
			"F",
			"D",
			"Z",
			"C",
		} ;
		Class [] listc = {
			byte.class,
			short.class,
			int.class,
			long.class,
			float.class,
			double.class,
			boolean.class,
			char.class,
			byte.class,
			short.class,
			int.class,
			long.class,
			float.class,
			double.class,
			boolean.class,
			char.class,
		} ;

		for (int i = 0 ; i < list.length ; i++){
			if (name.equals(list[i])){
				return listc[i] ;
			}
		}

		return null ;
	}


	boolean ClassIsPrimitive (Class p){
		String name = p.getName() ;

		if ((ClassIsNumeric(p))||(ClassIsString(p))||(ClassIsChar(p))||(ClassIsBool(p))){
			return true ;
		}

		InlineJavaUtils.debug(4, "class " + name + " is reference") ;
		return false ;
	}


	/*
		Determines if class is of numerical type.
	*/
	boolean ClassIsNumeric (Class p){
		String name = p.getName() ;

		Class [] list = {
			java.lang.Byte.class,
			java.lang.Short.class,
			java.lang.Integer.class,
			java.lang.Long.class,
			java.lang.Float.class,
			java.lang.Double.class,
			java.lang.Number.class,
			byte.class,
			short.class,
			int.class,
			long.class,
			float.class,
			double.class,
		} ;

		for (int i = 0 ; i < list.length ; i++){
			if (p == list[i]){
				InlineJavaUtils.debug(4, "class " + name + " is primitive numeric") ;
				return true ;
			}
		}

		return false ;
	}


	/*
		Class is String or StringBuffer
	*/
	boolean ClassIsString (Class p){
		String name = p.getName() ;

		Class [] list = {
			java.lang.String.class,
			java.lang.StringBuffer.class,
		} ;

		for (int i = 0 ; i < list.length ; i++){
			if (p == list[i]){
				InlineJavaUtils.debug(4, "class " + name + " is primitive string") ;
				return true ;
			}
		}

		return false ;
	}


	/*
		Class is Char
	*/
	boolean ClassIsChar (Class p){
		String name = p.getName() ;

		Class [] list = {
			java.lang.Character.class,
			char.class,
		} ;

		for (int i = 0 ; i < list.length ; i++){
			if (p == list[i]){
				InlineJavaUtils.debug(4, "class " + name + " is primitive char") ;
				return true ;
			}
		}

		return false ;
	}


	/*
		Class is Bool
	*/
	boolean ClassIsBool (Class p){
		String name = p.getName() ;

		Class [] list = {
			java.lang.Boolean.class,
			boolean.class,
		} ;

		for (int i = 0 ; i < list.length ; i++){
			if (p == list[i]){
				InlineJavaUtils.debug(4, "class " + name + " is primitive bool") ;
				return true ;
			}
		}

		return false ;
	}

	
	/*
		Determines if a class is not of a primitive type or of a 
		wrapper class.
	*/
	boolean ClassIsReference (Class p){
		String name = p.getName() ;

		if (ClassIsPrimitive(p)){
			return false ;
		}

		InlineJavaUtils.debug(4, "class " + name + " is reference") ;

		return true ;
	}

	boolean ClassIsArray (Class p){
		String name = p.getName() ;

		if ((ClassIsReference(p))&&(name.startsWith("["))){
			InlineJavaUtils.debug(4, "class " + name + " is array") ;
			return true ;
		}

		return false ;
	}
}