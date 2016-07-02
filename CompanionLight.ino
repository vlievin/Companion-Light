/*

Copyright (c) 2012-2014 RedBearLab

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

*/

//"SPI.h/Nordic_nRF8001.h/ble_shield.h" are needed in every new project
#include <SPI.h>
#include <Nordic_nRF8001.h>
#include <ble_shield.h>
 
// 3 5 9
#define R_PIN	3
#define G_PIN	5
#define B_PIN	9

/*----- BLE Utility -------------------------------------------------------------------------*/
// create peripheral instance, see pinouts above
//BLEPeripheral            blePeripheral        = BLEPeripheral(BLE_REQ, BLE_RDY, BLE_RST);

// create service
//BLEService	uartService          = BLEService("713d0000503e4c75ba943148f18d941e");

// create characteristic
//BLECharacteristic    txCharacteristic = BLECharacteristic("713d0002503e4c75ba943148f18d941e", BLENotify, 20);
//BLECharacteristic    rxCharacteristic = BLECharacteristic("713d0003503e4c75ba943148f18d941e", BLEWriteWithoutResponse, 20);
/*--------------------------------------------------------------------------------------------*/

boolean mAuto = true;

// Color arrays
int black[3]  = { 0, 0, 0 };
int white[3]  = { 255, 255, 255 };
int red[3]    = { 255, 0, 0 };
int green[3]  = { 0, 255, 0 };
int blue[3]   = { 0, 0, 255 };
int yellow[3] = { 40, 95, 0 };
int dimWhite[3] = { 30, 30, 30 };

int relaxs[5][3];
int relax1[3]  = {255, 13, 235 };
int relax2[3]  = {146, 12, 232 };
int relax3[3]  = {45, 0, 255 };
int relax4[3]  = {12, 66, 232 };
int relax5[3]  = {3, 171, 255 };
int relaxIndex = 0;
int dir = 1;



// Set initial color
int* redVal = new int(relax2[0]);
int* grnVal = new int(relax2[1]); 
int* bluVal = new int(relax2[2]);

int mHue = 0;
int mGain = 100;

unsigned long time;
unsigned long period = 12000;
unsigned long remaining_time = period;


void setup()
{
  ble_set_name("Companion");
  
  // Init. and start BLE library.
  ble_begin();
  
  // Enable serial debug
  Serial.begin(57600);
  
  time = millis();
  
  analogWrite(R_PIN, 255);
  analogWrite(G_PIN, 255);
  analogWrite(B_PIN, 255);
  
  memcpy( relaxs[0] , red, 3*sizeof(int) );
  memcpy( relaxs[1] , blue, 3*sizeof(int) );
  memcpy( relaxs[2] , green, 3*sizeof(int) );
  memcpy( relaxs[3] , blue, 3*sizeof(int) );
  memcpy( relaxs[4] , red, 3*sizeof(int) );
  
}

void loop()
{
  // If data is ready
  while(ble_available())
  {
    // read out command and data
    *redVal = (int)ble_read();
    *grnVal= (int)ble_read();
    *bluVal= (int)ble_read();
    mGain = (int)ble_read();
    byte dataMode = ble_read();
    
    switch(dataMode)
    {
    	case 0x00:
    	mAuto = false;
    	break;
    	
    	case 0x01:
    	mAuto = true;
    	break;
    	
    	case 0x02:
    	mGain = 0;
    	break;
    }
    updateColors();
    
  }
  updateColors();
  ble_do_events(); 
}

void updateColors()
{
	if (mAuto)
    {
    	float t = 300. * (float)(millis()-time)/(float)period;
    	hsi2rgb( t , 1, 1, redVal, grnVal, bluVal);
    	mGain = 0;
    }
    analogWrite(R_PIN, (float)(mGain * (*redVal)) / 100.0);
  	analogWrite(G_PIN, (float)(mGain * (*grnVal)) / 100.0);
    analogWrite(B_PIN, (float)(mGain * (*bluVal)) / 100.0);
}

int interpolation(int value, int target , float t)
{
	return (int)( (1-t) * (float)value + t * (float)target );
}

// found at http://blog.saikoled.com/post/43693602826/why-every-led-light-should-be-using-hsi
void hsi2rgb(float H, float S, float I, int* rr , int*gg, int*bb) {
  int r, g, b;
  H = fmod(H,360); // cycle H around to 0-360 degrees
  H = 3.14159*H/(float)180; // Convert to radians.
  S = S>0?(S<1?S:1):0; // clamp S and I to interval [0,1]
  I = I>0?(I<1?I:1):0;
    
  // Math! Thanks in part to Kyle Miller.
  if(H < 2.09439) {
    r = 255*I/3*(1+S*cos(H)/cos(1.047196667-H));
    g = 255*I/3*(1+S*(1-cos(H)/cos(1.047196667-H)));
    b = 255*I/3*(1-S);
  } else if(H < 4.188787) {
    H = H - 2.09439;
    g = 255*I/3*(1+S*cos(H)/cos(1.047196667-H));
    b = 255*I/3*(1+S*(1-cos(H)/cos(1.047196667-H)));
    r = 255*I/3*(1-S);
  } else {
    H = H - 4.188787;
    b = 255*I/3*(1+S*cos(H)/cos(1.047196667-H));
    r = 255*I/3*(1+S*(1-cos(H)/cos(1.047196667-H)));
    g = 255*I/3*(1-S);
  }
  *rr=r;
  *gg=g;
  *bb=b;
}