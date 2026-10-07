clear; clc;
rootDir = fileparts(mfilename('fullpath'));
sourceDir = fullfile(rootDir,'simulink','output');
outDir = fullfile(rootDir,'simulink','real_data_copies');
if ~exist(outDir,'dir'), mkdir(outDir); end

Km_est = 14.9;
tau_est = 0.174;
Ts = 0.1;

copyPhysical(sourceDir,outDir);
copyContinuous(sourceDir,outDir,Km_est,tau_est);
copyDiscrete(sourceDir,outDir,Km_est,tau_est,Ts);
buildConstantPlayback(outDir);
buildSquarePlayback(outDir);

fprintf('Real-data copies saved in: %s\n',outDir);

function closeIfLoaded(name)
    if bdIsLoaded(name), close_system(name,0); end
end

function exportAndClose(model,filePath)
    set_param(model,'ZoomFactor','FitSystem');
    save_system(model,filePath);
    print(['-s' model],'-dpng','-r180',strrep(filePath,'.slx','.png'));
    close_system(model,0);
end

function copyPhysical(sourceDir,outDir)
    src = fullfile(sourceDir,'model_01_real_system.slx');
    load_system(src);
    sourceName = 'model_01_real_system';
    targetName = 'actual_01_physical_system';
    closeIfLoaded(targetName);
    target = fullfile(outDir,[targetName '.slx']);
    save_system(sourceName,target);
    close_system(sourceName,0);
    load_system(target);
    set_param([targetName '/P controller (Kp)'],'Gain','2.2');
    set_param([targetName '/P controller (Kp)'],'AttributesFormatString', ...
        sprintf('Kp = 2.2 tracking\nKp = 3.0 step test'));
    set_param([targetName '/Encoder'],'AttributesFormatString', ...
        sprintf('11 PPR x 34\nCPR = 374 measured'));
    exportAndClose(targetName,target);
end

function copyContinuous(sourceDir,outDir,Km,tau)
    src = fullfile(sourceDir,'model_02_continuous.slx');
    load_system(src);
    sourceName = 'model_02_continuous';
    targetName = 'actual_02_continuous_estimated';
    closeIfLoaded(targetName);
    target = fullfile(outDir,[targetName '.slx']);
    save_system(sourceName,target);
    close_system(sourceName,0);
    load_system(target);
    set_param([targetName '/P controller (Kp)'],'Gain','3.0');
    set_param([targetName '/Continuous motor model'],'Numerator',num2str(Km,12));
    set_param([targetName '/Continuous motor model'],'Denominator',mat2str([tau 1 0],12));
    set_param([targetName '/Continuous motor model'],'AttributesFormatString', ...
        sprintf('Estimated from step data\nG(s) = 14.9/[s(0.174s+1)]'));
    exportAndClose(targetName,target);
end

function copyDiscrete(sourceDir,outDir,Km,tau,Ts)
    src = fullfile(sourceDir,'model_03_discrete.slx');
    load_system(src);
    sourceName = 'model_03_discrete';
    targetName = 'actual_03_discrete_estimated';
    closeIfLoaded(targetName);
    target = fullfile(outDir,[targetName '.slx']);
    save_system(sourceName,target);
    close_system(sourceName,0);
    load_system(target);
    Gd = c2d(tf(Km,[tau 1 0]),Ts,'zoh');
    [numd,dend] = tfdata(Gd,'v');
    set_param([targetName '/Discrete P controller (Kp)'],'Gain','3.0');
    set_param([targetName '/Discrete motor model G(z)'],'Numerator',mat2str(numd,12));
    set_param([targetName '/Discrete motor model G(z)'],'Denominator',mat2str(dend,12));
    set_param([targetName '/Discrete motor model G(z)'],'AttributesFormatString', ...
        sprintf(['Estimated plant, ZOH\n' ...
        'num = [0 0.3567 0.2946]\n' ...
        'den = [1 -1.5629 0.5629]\nTs = 0.1 s']));
    exportAndClose(targetName,target);
end

function buildPlayback(name,filePath,t,ref,pos,titleText)
    closeIfLoaded(name);
    new_system(name);
    set_param(name,'Location',[80 100 1350 650],'StopTime',num2str(t(end)));
    mws = get_param(name,'ModelWorkspace');
    assignin(mws,'reference_data',timeseries(ref,t));
    assignin(mws,'position_data',timeseries(pos,t));

    add_block('simulink/Sources/From Workspace',[name '/Measured reference'], ...
        'VariableName','reference_data','Position',[45 125 170 165]);
    add_block('simulink/Sources/From Workspace',[name '/Measured motor position'], ...
        'VariableName','position_data','Position',[45 270 170 310]);
    add_block('simulink/Math Operations/Sum',[name '/Tracking error'], ...
        'Inputs','+-','Position',[260 185 300 245]);
    add_block('simulink/Signal Routing/Mux',[name '/Reference and position'], ...
        'Inputs','2','Position',[380 105 390 175]);
    add_block('simulink/Sinks/Scope',[name '/Position scope'], ...
        'Position',[485 115 535 165]);
    add_block('simulink/Sinks/Scope',[name '/Error scope'], ...
        'Position',[485 220 535 270]);
    add_block('simulink/Sinks/Display',[name '/Final error'], ...
        'Position',[485 315 575 350]);
    add_block('simulink/Ports & Subsystems/Subsystem',[name '/Test information'], ...
        'Position',[670 135 920 270]);
    set_param([name '/Test information'],'AttributesFormatString',titleText);

    add_line(name,'Measured reference/1','Tracking error/1','autorouting','on');
    add_line(name,'Measured motor position/1','Tracking error/2','autorouting','on');
    add_line(name,'Measured reference/1','Reference and position/1','autorouting','on');
    add_line(name,'Measured motor position/1','Reference and position/2','autorouting','on');
    add_line(name,'Reference and position/1','Position scope/1','autorouting','on');
    add_line(name,'Tracking error/1','Error scope/1','autorouting','on');
    add_line(name,'Tracking error/1','Final error/1','autorouting','on');
    exportAndClose(name,filePath);
end

function buildConstantPlayback(outDir)
    t = (0.1:0.1:5.4)';
    ref = 45*ones(size(t));
    pos = 46.2*ones(size(t));
    pos(1:9) = [0;59.68;70.27;32.73;32.73;51.98;51.98;47.17;46.20];
    buildPlayback('actual_04_constant_measured', ...
        fullfile(outDir,'actual_04_constant_measured.slx'),t,ref,pos, ...
        sprintf('REAL EXPERIMENTAL DATA\n45-degree step\nKp = 3.0, Ts = 0.1 s'));
end

function buildSquarePlayback(outDir)
    source = fullfile(fileparts(mfilename('fullpath')), 'data', 'square_response.txt');
    text = fileread(source);
    tokens = regexp(text, ...
        'Tiempo:\s*(\d+)\s*ms\s*\|\s*Ref:\s*(-?[\d.]+).*?Angulo:\s*(-?[\d.]+)', ...
        'tokens');
    n = numel(tokens); t = zeros(n,1); ref = zeros(n,1); pos = zeros(n,1);
    for k = 1:n
        t(k) = str2double(tokens{k}{1})/1000;
        ref(k) = str2double(tokens{k}{2});
        pos(k) = str2double(tokens{k}{3});
    end
    buildPlayback('actual_05_square_measured', ...
        fullfile(outDir,'actual_05_square_measured.slx'),t,ref,pos, ...
        sprintf('REAL EXPERIMENTAL DATA\n+/-45 degrees every 3 s\nKp = 2.2, Ts = 0.1 s'));
end
