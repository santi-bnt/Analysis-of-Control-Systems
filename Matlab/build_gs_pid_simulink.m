clear; clc;

% Lazo PID simple para G(s) = 144 / (s^2 + 7.2s + 144)
rootDir = fileparts(mfilename('fullpath'));
outDir = fullfile(rootDir, 'simulink', 'gs_response');
if ~exist(outDir, 'dir')
    mkdir(outDir);
end

modelName = 'gs_pid_simple';
modelFile = fullfile(outDir, [modelName '.slx']);

if bdIsLoaded(modelName)
    close_system(modelName, 0);
end
if exist(modelFile, 'file')
    delete(modelFile);
end

% Ganancias del PID.
Kp = 4.5122;
Ki = 21.0020;
Kd = 0.1834;
N = 100;               % Filtro derivativo: Tf = 1/N = 0.01 s

new_system(modelName);
set_param(modelName, ...
    'Location', [80 100 1250 620], ...
    'StopTime', '2', ...
    'SolverType', 'Variable-step', ...
    'Solver', 'ode45', ...
    'MaxStep', '0.001');

ws = get_param(modelName, 'ModelWorkspace');
assignin(ws, 'Kp', Kp);
assignin(ws, 'Ki', Ki);
assignin(ws, 'Kd', Kd);
assignin(ws, 'N', N);
assignin(ws, 'Gs_num', 144);
assignin(ws, 'Gs_den', [1 7.2 144]);

add_block('simulink/Sources/Step', [modelName '/Escalon'], ...
    'Time', '0.1', 'Before', '0', 'After', '1', ...
    'Position', [45 200 80 230]);

add_block('simulink/Math Operations/Sum', [modelName '/Error'], ...
    'Inputs', '+-', 'IconShape', 'round', ...
    'Position', [145 185 185 245]);

add_block('simulink/Continuous/PID Controller', [modelName '/PID'], ...
    'P', 'Kp', 'I', 'Ki', 'D', 'Kd', 'N', 'N', ...
    'Position', [255 175 400 255]);
set_param([modelName '/PID'], 'AttributesFormatString', ...
    sprintf('Kp=%.4f  Ki=%.4f  Kd=%.4f', Kp, Ki, Kd));

add_block('simulink/Continuous/Transfer Fcn', [modelName '/Gs'], ...
    'Numerator', 'Gs_num', 'Denominator', 'Gs_den', ...
    'Position', [510 175 695 255]);
set_param([modelName '/Gs'], 'AttributesFormatString', ...
    '144/(s^2 + 7.2s + 144)');

add_block('simulink/Sinks/Scope', [modelName '/Scope respuesta'], ...
    'NumInputPorts', '2', ...
    'Position', [880 165 945 225]);

add_line(modelName, 'Escalon/1', 'Error/1', 'autorouting', 'on');
add_line(modelName, 'Error/1', 'PID/1', 'autorouting', 'on');
add_line(modelName, 'PID/1', 'Gs/1', 'autorouting', 'on');
add_line(modelName, 'Gs/1', 'Error/2', 'autorouting', 'on');
add_line(modelName, 'Escalon/1', 'Scope respuesta/1', ...
    'autorouting', 'on');
add_line(modelName, 'Gs/1', 'Scope respuesta/2', ...
    'autorouting', 'on');

add_block('built-in/Note', [modelName '/Descripcion'], ...
    'Position', [45 425 510 490], ...
    'Text', sprintf('PID simple: Kp = %.4f, Ki = %.4f, Kd = %.4f', ...
                    Kp, Ki, Kd), ...
    'FontSize', '12');

set_param(modelName, 'SimulationCommand', 'update');
save_system(modelName, modelFile);
print(['-s' modelName], '-dpng', '-r160', ...
    fullfile(outDir, 'gs_pid_simple_model.png'));

% Simulacion de comprobacion.
sim(modelName);
close_system(modelName, 0);

fprintf('Modelo PID simple creado: %s\n', modelFile);
