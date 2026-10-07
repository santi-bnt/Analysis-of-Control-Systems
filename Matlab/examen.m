clc;
clear;
close all;

%% Parametros

% Mi matricula es A01646121
% El ultimo digito es 1
% Por lo tanto R1 = R2 = 1 + 10 = 11 ohms

R1 = 11;
R2 = 11;
R3 = 10;

C1 = 0.1;
C2 = 0.1;

E = 10;

%% Tiempo de simulacion

tspan = [0 20];

%% Condiciones iniciales
% Inicialmente los capacitores estan descargados

VC1_0 = 0;
VC2_0 = 0;

y0 = [VC1_0 VC2_0];

%% Modelo del circuito
% y(1) = VC1
% y(2) = VC2

modelo = @(t,y) [

    (E - y(1)) / ((R1 + R2)*C1);

    (E - y(2)) / (R3*C2)

];

%% Simulacion

[t,y] = ode45(modelo, tspan, y0);

VC1 = y(:,1);
VC2 = y(:,2);

%% Estado estable

VC1_ss = E;
VC2_ss = E;

fprintf('Valores de estado estable:\n');
fprintf('VC1 = %.4f V\n', VC1_ss);
fprintf('VC2 = %.4f V\n', VC2_ss);

fprintf('\nValores al final de la simulacion:\n');
fprintf('VC1(20 s) = %.4f V\n', VC1(end));
fprintf('VC2(20 s) = %.4f V\n', VC2(end));

%% Grafica VC1

figure;

plot(t, VC1, 'LineWidth', 2);
hold on;

yline(VC1_ss, '--', 'Steady State');

grid on;

xlabel('Time (s)');
ylabel('V_{C1} (V)');
title('V_{C1} vs Time');

%% Grafica VC2

figure;

plot(t, VC2, 'LineWidth', 2);
hold on;

yline(VC2_ss, '--', 'Steady State');

grid on;

xlabel('Time (s)');
ylabel('V_{C2} (V)');
title('V_{C2} vs Time');
