-- Migration: remplacer les codes de type de mouvement (Entrée, Sortie, etc.) par des majuscules (ENTREE, SORTIE, etc.)
-- À exécuter manuellement si votre base a déjà les anciens codes.

-- 1) Insérer les nouveaux types (codes majuscules) s'ils n'existent pas
INSERT INTO public.tp_movement_type (code, label, description, allows_negative, requires_destination, is_active)
SELECT 'ENTREE', 'Entrée', 'Ajout de produits en stock', false, false, true
WHERE NOT EXISTS (SELECT 1 FROM public.tp_movement_type WHERE code = 'ENTREE');

INSERT INTO public.tp_movement_type (code, label, description, allows_negative, requires_destination, is_active)
SELECT 'SORTIE', 'Sortie', 'Retrait de produits du stock', false, false, true
WHERE NOT EXISTS (SELECT 1 FROM public.tp_movement_type WHERE code = 'SORTIE');

INSERT INTO public.tp_movement_type (code, label, description, allows_negative, requires_destination, is_active)
SELECT 'TRANSFERT', 'Transfert', 'Déplacement de produits entre entrepôts', false, true, true
WHERE NOT EXISTS (SELECT 1 FROM public.tp_movement_type WHERE code = 'TRANSFERT');

INSERT INTO public.tp_movement_type (code, label, description, allows_negative, requires_destination, is_active)
SELECT 'AJUSTEMENT', 'Ajustement', 'Correction de stock (peut être positif ou négatif)', true, false, true
WHERE NOT EXISTS (SELECT 1 FROM public.tp_movement_type WHERE code = 'AJUSTEMENT');

-- 2) Mettre à jour les mouvements pour qu'ils pointent vers les nouveaux codes
UPDATE public.td_movements SET type_code = 'ENTREE'   WHERE type_code = 'Entrée';
UPDATE public.td_movements SET type_code = 'SORTIE'   WHERE type_code = 'Sortie';
UPDATE public.td_movements SET type_code = 'TRANSFERT' WHERE type_code = 'Transfert';
UPDATE public.td_movements SET type_code = 'AJUSTEMENT' WHERE type_code = 'Ajustement';

-- 3) Supprimer les anciens types (une fois qu'aucun mouvement ne les référence)
DELETE FROM public.tp_movement_type WHERE code = 'Entrée';
DELETE FROM public.tp_movement_type WHERE code = 'Sortie';
DELETE FROM public.tp_movement_type WHERE code = 'Transfert';
DELETE FROM public.tp_movement_type WHERE code = 'Ajustement';
