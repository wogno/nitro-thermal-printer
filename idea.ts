const data = [
    // print text
    'Line 1: Hello, World!',
    // pint image with style or without style style(optional)
    [Image, style],
    [
        [
            ['Line 2: This is a test.', 'Line 3: Printing images with styles.', ...]
          ['Line 3', 'Info 2', ..]
        ],
        style//(applied for all columns)
    ],
    // print QR code
    [QRCode, 'https://example.com' , style],
]
BLEPrinter.printBulk(data);
// to start printing 
BLEPrinter.printBill("Text to print", )