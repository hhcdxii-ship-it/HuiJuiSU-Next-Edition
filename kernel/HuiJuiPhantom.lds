SECTIONS {
	__patchable_function_entries : { *(__patchable_function_entries) }
	.text : {
		$x.cfi_jt = . + 0x2954;
		$x.cfi_jt = . + 0x295c;
		$x.cfi_jt = . + 0x2964;
		$x.cfi_jt = . + 0x296c;
		$x.cfi_jt = . + 0x297c;
		$x.cfi_jt = . + 0x2984;
		$x.cfi_jt = . + 0x298c;
		$x.cfi_jt = . + 0x2994;
		$x.cfi_jt = . + 0x29b4;
		$x.cfi_jt = . + 0x29bc;
		$x.cfi_jt = . + 0x29c4;
		$x.cfi_jt = . + 0x29cc;
		$x.cfi_jt = . + 0x29ec;
		$x.cfi_jt = . + 0x29f4;
		$x.cfi_jt = . + 0x29fc;
		$x.cfi_jt = . + 0x2a04;
		$x.cfi_jt = . + 0x2a0c;
		$x.cfi_jt = . + 0x2a14;
	}
}
