clear; clc;
rootDir = fileparts(mfilename('fullpath'));
outDir = fullfile(rootDir,'simulink','output');

translateReal(fullfile(outDir,'model_01_real_system.slx'));
translateContinuous(fullfile(outDir,'model_02_continuous.slx'));
translateDiscrete(fullfile(outDir,'model_03_discrete.slx'));

function clearSelection(model)
    blocks = find_system(model,'FindAll','on','Type','Block');
    for k = 1:numel(blocks)
        try
            set_param(blocks(k),'Selected','off');
        catch
        end
    end
    try
        hilite_system(model,'none');
    catch
    end
end

function renameBlock(model,oldName,newName)
    oldPath = [model '/' oldName];
    if getSimulinkBlockHandle(oldPath) ~= -1
        set_param(oldPath,'Name',newName);
    end
end

function finish(model,filePath)
    clearSelection(model);
    set_param(model,'ZoomFactor','FitSystem');
    save_system(model,filePath);
    print(['-s' model],'-dpng','-r180',strrep(filePath,'.slx','.png'));
    close_system(model,0);
end

function translateReal(filePath)
    [~,m] = fileparts(filePath);
    load_system(filePath);
    renameBlock(m,'Referencia','Angular reference');
    renameBlock(m,'Error','Position error');
    renameBlock(m,'Control P','P controller (Kp)');
    renameBlock(m,'PWM -255 a 255','PWM saturation');
    renameBlock(m,'ESP32 direccion PWM','ESP32 PWM direction');
    renameBlock(m,'Motor DC y reductor','DC motor and gearbox');
    renameBlock(m,'Posicion theta','Angular position theta');
    set_param([m '/P controller (Kp)'],'Gain','Kp');
    set_param([m '/PWM saturation'],'AttributesFormatString','-255 <= u <= 255');
    set_param([m '/ESP32 PWM direction'],'AttributesFormatString',sprintf('Ts = 0.1 s\nRPWM when u > 0\nLPWM when u < 0'));
    set_param([m '/BTS7960'],'AttributesFormatString','5 kHz, 8-bit PWM');
    set_param([m '/DC motor and gearbox'],'AttributesFormatString',sprintf('Physical plant\nGear ratio 34:1'));
    set_param([m '/Encoder'],'AttributesFormatString',sprintf('11 PPR x 34\n374 CPR'));
    finish(m,filePath);
end

function translateContinuous(filePath)
    [~,m] = fileparts(filePath);
    load_system(filePath);
    renameBlock(m,'Referencia 45 grados','45-degree reference');
    renameBlock(m,'Error','Position error');
    renameBlock(m,'Kp','P controller (Kp)');
    renameBlock(m,'Saturacion PWM','PWM saturation');
    renameBlock(m,'Planta continua','Continuous motor model');
    renameBlock(m,'Posicion theta','Angular position theta');
    set_param([m '/P controller (Kp)'],'Gain','Kp');
    set_param([m '/PWM saturation'],'AttributesFormatString','-255 <= u <= 255');
    set_param([m '/Continuous motor model'],'AttributesFormatString','G(s) = Km / [s(tau*s + 1)]');
    finish(m,filePath);
end

function translateDiscrete(filePath)
    [~,m] = fileparts(filePath);
    load_system(filePath);
    renameBlock(m,'Referencia discreta','Discrete reference');
    renameBlock(m,'Error k','Position error k');
    renameBlock(m,'Kp discreto','Discrete P controller (Kp)');
    renameBlock(m,'Saturacion PWM','PWM saturation');
    renameBlock(m,'Planta discreta Gz','Discrete motor model G(z)');
    renameBlock(m,'Retardo de muestreo','Sample delay z^-1');
    renameBlock(m,'Posicion theta k','Angular position theta k');
    set_param([m '/Discrete P controller (Kp)'],'Gain','Kp');
    set_param([m '/PWM saturation'],'AttributesFormatString','-255 <= u <= 255');
    set_param([m '/Discrete motor model G(z)'],'AttributesFormatString','ZOH, Ts = 0.1 s');
    finish(m,filePath);
end
