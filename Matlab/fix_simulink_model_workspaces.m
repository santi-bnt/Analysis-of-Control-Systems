clear; clc;
rootDir = fileparts(mfilename('fullpath'));
modelDir = fullfile(rootDir,'simulink','output');

fixPhysical(fullfile(modelDir,'model_01_real_system.slx'));
fixContinuous(fullfile(modelDir,'model_02_continuous.slx'));
fixDiscrete(fullfile(modelDir,'model_03_discrete.slx'));

function finish(model,filePath)
    blocks = find_system(model,'FindAll','on','Type','Block');
    for k = 1:numel(blocks)
        try, set_param(blocks(k),'Selected','off'); catch, end
    end
    try, hilite_system(model,'none'); catch, end
    save_system(model,filePath);
    print(['-s' model],'-dpng','-r180',strrep(filePath,'.slx','.png'));
    close_system(model,0);
end

function fixPhysical(filePath)
    [~,m] = fileparts(filePath); load_system(filePath);
    ws = get_param(m,'ModelWorkspace');
    assignin(ws,'Kp',2.2);
    assignin(ws,'Ts',0.1);
    set_param([m '/P controller (Kp)'],'Gain','Kp');
    set_param([m '/P controller (Kp)'],'AttributesFormatString','Kp = 2.2');
    finish(m,filePath);
end

function fixContinuous(filePath)
    [~,m] = fileparts(filePath); load_system(filePath);
    ws = get_param(m,'ModelWorkspace');
    assignin(ws,'Kp',3.0);
    assignin(ws,'Km',14.9);
    assignin(ws,'tau',0.174);
    assignin(ws,'Ts',0.1);
    set_param([m '/P controller (Kp)'],'Gain','Kp');
    set_param([m '/Continuous motor model'],'Numerator','Km');
    set_param([m '/Continuous motor model'],'Denominator','[tau 1 0]');
    set_param([m '/P controller (Kp)'],'AttributesFormatString','Kp = 3.0');
    finish(m,filePath);
end

function fixDiscrete(filePath)
    [~,m] = fileparts(filePath); load_system(filePath);
    ws = get_param(m,'ModelWorkspace');
    assignin(ws,'Kp',3.0);
    assignin(ws,'Ts',0.1);
    assignin(ws,'numd',[0 0.3567 0.2946]);
    assignin(ws,'dend',[1 -1.5629 0.5629]);
    set_param([m '/Discrete P controller (Kp)'],'Gain','Kp');
    set_param([m '/Discrete motor model G(z)'],'Numerator','numd');
    set_param([m '/Discrete motor model G(z)'],'Denominator','dend');
    set_param([m '/Discrete motor model G(z)'],'SampleTime','Ts');
    set_param([m '/Sample delay z^-1'],'SampleTime','Ts');
    set_param([m '/Discrete P controller (Kp)'],'AttributesFormatString','Kp = 3.0');
    finish(m,filePath);
end
