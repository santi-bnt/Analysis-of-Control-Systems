clear; clc;
rootDir = fileparts(mfilename('fullpath'));
outDir = fullfile(rootDir,'simulink','output');
if ~exist(outDir,'dir'), mkdir(outDir); end
assignin('base','Kp',2.2); assignin('base','Ts',0.1);
assignin('base','Km',14.9); assignin('base','tau',0.174);

makeReal(fullfile(outDir,'model_01_real_system.slx'));
makeContinuous(fullfile(outDir,'model_02_continuous.slx'));
makeDiscrete(fullfile(outDir,'model_03_discrete.slx'));
run(fullfile(rootDir,'translate_simulink_models.m'));

function base(name)
if bdIsLoaded(name), close_system(name,0); end
new_system(name); set_param(name,'Location',[60 80 1450 700]);
ws = get_param(name,'ModelWorkspace');
assignin(ws,'Kp',2.2); assignin(ws,'Ts',0.1);
assignin(ws,'Km',14.9); assignin(ws,'tau',0.174);
end
function wire(m,a,b), add_line(m,a,b,'autorouting','on'); end
function makeReal(file)
[~,m]=fileparts(file); base(m);
add_block('simulink/Sources/In1',[m '/Angular reference'],'Position',[30 220 60 240]);
add_block('simulink/Math Operations/Sum',[m '/Position error'],'Inputs','+-','IconShape','round','Position',[115 200 155 260]);
add_block('simulink/Math Operations/Gain',[m '/P controller (Kp)'],'Gain','Kp','Position',[215 205 300 255]);
add_block('simulink/Discontinuities/Saturation',[m '/PWM saturation'],'UpperLimit','255','LowerLimit','-255','Position',[355 200 465 260]);
add_block('simulink/Ports & Subsystems/Subsystem',[m '/ESP32 PWM direction'],'Position',[525 185 680 275]);
set_param([m '/ESP32 PWM direction'],'AttributesFormatString','Ts=0.1 s\nRPWM: u>0\nLPWM: u<0');
add_block('simulink/Ports & Subsystems/Subsystem',[m '/BTS7960'],'Position',[745 195 865 265]);
set_param([m '/BTS7960'],'AttributesFormatString','5 kHz, 8 bits');
add_block('simulink/Ports & Subsystems/Subsystem',[m '/DC motor and gearbox'],'Position',[930 180 1090 280]);
set_param([m '/DC motor and gearbox'],'AttributesFormatString','Physical plant\nGear ratio 34:1');
add_block('simulink/Ports & Subsystems/Subsystem',[m '/Encoder'],'Position',[710 380 860 460]);
set_param([m '/Encoder'],'AttributesFormatString','11 PPR x 34\n374 CPR');
add_block('simulink/Sinks/Out1',[m '/Angular position theta'],'Position',[1160 220 1190 240]);
wire(m,'Angular reference/1','Position error/1'); wire(m,'Position error/1','P controller (Kp)/1'); wire(m,'P controller (Kp)/1','PWM saturation/1');
wire(m,'PWM saturation/1','ESP32 PWM direction/1'); wire(m,'ESP32 PWM direction/1','BTS7960/1');
wire(m,'BTS7960/1','DC motor and gearbox/1'); wire(m,'DC motor and gearbox/1','Angular position theta/1');
wire(m,'DC motor and gearbox/1','Encoder/1'); wire(m,'Encoder/1','Position error/2');
save_system(m,file); close_system(m,0);
end
function makeContinuous(file)
[~,m]=fileparts(file); base(m);
ws = get_param(m,'ModelWorkspace'); assignin(ws,'Kp',3.0);
add_block('simulink/Sources/Step',[m '/45-degree reference'],'After','45','Position',[35 220 70 250]);
add_block('simulink/Math Operations/Sum',[m '/Position error'],'Inputs','+-','IconShape','round','Position',[130 205 170 265]);
add_block('simulink/Math Operations/Gain',[m '/P controller (Kp)'],'Gain','Kp','Position',[230 210 315 260]);
add_block('simulink/Discontinuities/Saturation',[m '/PWM saturation'],'UpperLimit','255','LowerLimit','-255','Position',[375 205 480 265]);
add_block('simulink/Continuous/Transfer Fcn',[m '/Continuous motor model'],'Numerator','Km','Denominator','[tau 1 0]','Position',[555 195 750 275]);
set_param([m '/Continuous motor model'],'AttributesFormatString','G(s)=Km/[s(tau*s+1)]');
add_block('simulink/Sinks/Out1',[m '/Angular position theta'],'Position',[850 225 880 245]);
wire(m,'45-degree reference/1','Position error/1'); wire(m,'Position error/1','P controller (Kp)/1'); wire(m,'P controller (Kp)/1','PWM saturation/1');
wire(m,'PWM saturation/1','Continuous motor model/1'); wire(m,'Continuous motor model/1','Angular position theta/1'); wire(m,'Continuous motor model/1','Position error/2');
save_system(m,file); close_system(m,0);
end
function makeDiscrete(file)
[~,m]=fileparts(file); base(m); set_param(m,'Solver','FixedStepDiscrete','FixedStep','0.1');
ws = get_param(m,'ModelWorkspace'); assignin(ws,'Kp',3.0);
Gd=c2d(tf(14.9,[0.174 1 0]),0.1,'zoh'); [numd,dend]=tfdata(Gd,'v');
assignin(ws,'numd',numd); assignin(ws,'dend',dend);
add_block('simulink/Sources/Step',[m '/Discrete reference'],'After','45','SampleTime','0.1','Position',[35 220 70 250]);
add_block('simulink/Math Operations/Sum',[m '/Position error k'],'Inputs','+-','IconShape','round','Position',[130 205 170 265]);
add_block('simulink/Math Operations/Gain',[m '/Discrete P controller (Kp)'],'Gain','Kp','SampleTime','0.1','Position',[230 210 330 260]);
add_block('simulink/Discontinuities/Saturation',[m '/PWM saturation'],'UpperLimit','255','LowerLimit','-255','Position',[390 205 495 265]);
add_block('simulink/Discrete/Discrete Transfer Fcn',[m '/Discrete motor model G(z)'],'Numerator','numd','Denominator','dend','SampleTime','0.1','Position',[570 195 755 275]);
set_param([m '/Discrete motor model G(z)'],'AttributesFormatString','ZOH, Ts=0.1 s');
add_block('simulink/Discrete/Unit Delay',[m '/Sample delay z^-1'],'SampleTime','0.1','Position',[575 380 725 430]);
add_block('simulink/Sinks/Out1',[m '/Angular position theta k'],'Position',[850 225 880 245]);
wire(m,'Discrete reference/1','Position error k/1'); wire(m,'Position error k/1','Discrete P controller (Kp)/1'); wire(m,'Discrete P controller (Kp)/1','PWM saturation/1');
wire(m,'PWM saturation/1','Discrete motor model G(z)/1'); wire(m,'Discrete motor model G(z)/1','Angular position theta k/1');
wire(m,'Discrete motor model G(z)/1','Sample delay z^-1/1'); wire(m,'Sample delay z^-1/1','Position error k/2');
save_system(m,file); close_system(m,0);
end
