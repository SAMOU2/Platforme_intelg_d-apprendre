--
-- PostgreSQL database dump
--

\restrict d3WtFVvXeusMLEIVqywmkA1stv0V028T51CUAxQ0Je5zXvhIBTYhg2NE0iYZG2K

-- Dumped from database version 16.15 (Debian 16.15-1.pgdg12+2)
-- Dumped by pg_dump version 16.15 (Debian 16.15-1.pgdg12+2)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: pgcrypto; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;


--
-- Name: EXTENSION pgcrypto; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION pgcrypto IS 'cryptographic functions';


--
-- Name: vector; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;


--
-- Name: EXTENSION vector; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION vector IS 'vector data type and ivfflat and hnsw access methods';


--
-- Name: etat_niveau; Type: TYPE; Schema: public; Owner: ihm2026
--

CREATE TYPE public.etat_niveau AS ENUM (
    'VERROUILLE',
    'EN_COURS',
    'VALIDE'
);


ALTER TYPE public.etat_niveau OWNER TO ihm2026;

--
-- Name: role_utilisateur; Type: TYPE; Schema: public; Owner: ihm2026
--

CREATE TYPE public.role_utilisateur AS ENUM (
    'ADMINISTRATEUR',
    'ENSEIGNANT',
    'APPRENANT'
);


ALTER TYPE public.role_utilisateur OWNER TO ihm2026;

--
-- Name: statut_contenu; Type: TYPE; Schema: public; Owner: ihm2026
--

CREATE TYPE public.statut_contenu AS ENUM (
    'BROUILLON',
    'PUBLIE',
    'BROUILLON_IA'
);


ALTER TYPE public.statut_contenu OWNER TO ihm2026;

--
-- Name: statut_tentative; Type: TYPE; Schema: public; Owner: ihm2026
--

CREATE TYPE public.statut_tentative AS ENUM (
    'EN_COURS',
    'SOUMISE',
    'CORRIGEE',
    'ANNULEE'
);


ALTER TYPE public.statut_tentative OWNER TO ihm2026;

--
-- Name: type_question; Type: TYPE; Schema: public; Owner: ihm2026
--

CREATE TYPE public.type_question AS ENUM (
    'QCM',
    'REPONSE_COURTE',
    'CODE'
);


ALTER TYPE public.type_question OWNER TO ihm2026;

--
-- Name: proteger_validation(); Type: FUNCTION; Schema: public; Owner: ihm2026
--

CREATE FUNCTION public.proteger_validation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF OLD.etat = 'VALIDE' AND NEW.etat <> 'VALIDE' THEN
        RAISE EXCEPTION 'Une validation acquise ne peut pas être retirée (progressions.id=%)', OLD.id;
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION public.proteger_validation() OWNER TO ihm2026;

--
-- Name: rechercher_fragments(public.vector, text, text, integer, double precision); Type: FUNCTION; Schema: public; Owner: ihm2026
--

CREATE FUNCTION public.rechercher_fragments(question_vecteur public.vector, question_texte text, filtre_langage text DEFAULT NULL::text, k integer DEFAULT 5, seuil_vecteur double precision DEFAULT 0.05) RETURNS TABLE(fragment_id bigint, section text, langage text, contenu text, similitude double precision, lexical double precision, score double precision)
    LANGUAGE sql STABLE
    AS $$
    WITH candidats AS (
        SELECT f.id,
               f.section,
               f.langage,
               f.contenu,
               1 - (f.vecteur <=> question_vecteur)                        AS sim,
               ts_rank_cd(to_tsvector('french', f.contenu),
                          plainto_tsquery('french', question_texte))::double precision AS lex
        FROM ia_fragments f
        WHERE f.vecteur IS NOT NULL
          AND (filtre_langage IS NULL OR f.langage = filtre_langage)
    )
    SELECT c.id, c.section, c.langage, c.contenu, c.sim, c.lex,
           c.sim + (0.5 * c.lex) AS score
    FROM candidats c
    -- Un fragment n'est candidat que s'il est proche OU lexicalement pertinent :
    -- sans ce filtre, la branche vectorielle renvoie toujours k lignes, même
    -- pour une question hors domaine (constaté et corrigé le 2026-10-27).
    WHERE c.sim >= seuil_vecteur OR c.lex > 0
    ORDER BY score DESC
    LIMIT k;
$$;


ALTER FUNCTION public.rechercher_fragments(question_vecteur public.vector, question_texte text, filtre_langage text, k integer, seuil_vecteur double precision) OWNER TO ihm2026;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: affectations_pedagogiques; Type: TABLE; Schema: public; Owner: ihm2026
--

CREATE TABLE public.affectations_pedagogiques (
    apprenant_id bigint NOT NULL,
    enseignant_id bigint NOT NULL,
    affecte_par bigint NOT NULL,
    maj_le timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT affectations_pedagogiques_check CHECK ((apprenant_id <> enseignant_id))
);


ALTER TABLE public.affectations_pedagogiques OWNER TO ihm2026;

--
-- Name: agent_ia; Type: TABLE; Schema: public; Owner: ihm2026
--

CREATE TABLE public.agent_ia (
    id bigint NOT NULL,
    nom text NOT NULL,
    actif boolean DEFAULT true NOT NULL,
    positionnement text NOT NULL,
    quota_jour integer DEFAULT 200 NOT NULL,
    cree_le timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT agent_ia_positionnement_check CHECK ((positionnement = ANY (ARRAY['A'::text, 'B'::text])))
);


ALTER TABLE public.agent_ia OWNER TO ihm2026;

--
-- Name: agent_ia_id_seq; Type: SEQUENCE; Schema: public; Owner: ihm2026
--

CREATE SEQUENCE public.agent_ia_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.agent_ia_id_seq OWNER TO ihm2026;

--
-- Name: agent_ia_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ihm2026
--

ALTER SEQUENCE public.agent_ia_id_seq OWNED BY public.agent_ia.id;


--
-- Name: aides_ia; Type: TABLE; Schema: public; Owner: ihm2026
--

CREATE TABLE public.aides_ia (
    id bigint NOT NULL,
    apprenant_id bigint NOT NULL,
    tentative_id bigint,
    agent_id bigint,
    question text NOT NULL,
    reponse text NOT NULL,
    modele text,
    cree_le timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.aides_ia OWNER TO ihm2026;

--
-- Name: aides_ia_id_seq; Type: SEQUENCE; Schema: public; Owner: ihm2026
--

CREATE SEQUENCE public.aides_ia_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.aides_ia_id_seq OWNER TO ihm2026;

--
-- Name: aides_ia_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ihm2026
--

ALTER SEQUENCE public.aides_ia_id_seq OWNED BY public.aides_ia.id;


--
-- Name: blocs; Type: TABLE; Schema: public; Owner: ihm2026
--

CREATE TABLE public.blocs (
    id bigint NOT NULL,
    cours_id bigint NOT NULL,
    "position" integer NOT NULL,
    type text NOT NULL,
    langage text,
    contenu text NOT NULL,
    CONSTRAINT blocs_type_check CHECK ((type = ANY (ARRAY['TEXTE'::text, 'CODE'::text, 'IMAGE'::text])))
);


ALTER TABLE public.blocs OWNER TO ihm2026;

--
-- Name: blocs_id_seq; Type: SEQUENCE; Schema: public; Owner: ihm2026
--

CREATE SEQUENCE public.blocs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.blocs_id_seq OWNER TO ihm2026;

--
-- Name: blocs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ihm2026
--

ALTER SEQUENCE public.blocs_id_seq OWNED BY public.blocs.id;


--
-- Name: codes_connexion; Type: TABLE; Schema: public; Owner: ihm2026
--

CREATE TABLE public.codes_connexion (
    identifiant uuid NOT NULL,
    utilisateur_id bigint NOT NULL,
    empreinte_code text NOT NULL,
    expire_le timestamp with time zone NOT NULL,
    echecs integer DEFAULT 0 NOT NULL,
    cree_le timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT codes_connexion_echecs_check CHECK ((echecs >= 0))
);


ALTER TABLE public.codes_connexion OWNER TO ihm2026;

--
-- Name: cours; Type: TABLE; Schema: public; Owner: ihm2026
--

CREATE TABLE public.cours (
    id bigint NOT NULL,
    niveau_id bigint NOT NULL,
    titre text NOT NULL,
    statut public.statut_contenu DEFAULT 'BROUILLON'::public.statut_contenu NOT NULL,
    maj_le timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.cours OWNER TO ihm2026;

--
-- Name: cours_id_seq; Type: SEQUENCE; Schema: public; Owner: ihm2026
--

CREATE SEQUENCE public.cours_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.cours_id_seq OWNER TO ihm2026;

--
-- Name: cours_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ihm2026
--

ALTER SEQUENCE public.cours_id_seq OWNED BY public.cours.id;


--
-- Name: entrainements_niveaux; Type: TABLE; Schema: public; Owner: ihm2026
--

CREATE TABLE public.entrainements_niveaux (
    id bigint NOT NULL,
    niveau_id bigint NOT NULL,
    enonce text NOT NULL,
    code_depart text DEFAULT ''::text NOT NULL,
    publie boolean DEFAULT false NOT NULL,
    cree_par bigint,
    maj_le timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT entrainements_niveaux_enonce_check CHECK (((length(enonce) >= 1) AND (length(enonce) <= 12000)))
);


ALTER TABLE public.entrainements_niveaux OWNER TO ihm2026;

--
-- Name: entrainements_niveaux_id_seq; Type: SEQUENCE; Schema: public; Owner: ihm2026
--

CREATE SEQUENCE public.entrainements_niveaux_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.entrainements_niveaux_id_seq OWNER TO ihm2026;

--
-- Name: entrainements_niveaux_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ihm2026
--

ALTER SEQUENCE public.entrainements_niveaux_id_seq OWNED BY public.entrainements_niveaux.id;


--
-- Name: ia_fragments; Type: TABLE; Schema: public; Owner: ihm2026
--

CREATE TABLE public.ia_fragments (
    id bigint NOT NULL,
    niveau_id bigint,
    cours_id bigint,
    langage text NOT NULL,
    section text NOT NULL,
    ordre integer DEFAULT 0 NOT NULL,
    contenu text NOT NULL,
    caracteres integer NOT NULL,
    vecteur public.vector(1536)
);


ALTER TABLE public.ia_fragments OWNER TO ihm2026;

--
-- Name: ia_fragments_id_seq; Type: SEQUENCE; Schema: public; Owner: ihm2026
--

CREATE SEQUENCE public.ia_fragments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.ia_fragments_id_seq OWNER TO ihm2026;

--
-- Name: ia_fragments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ihm2026
--

ALTER SEQUENCE public.ia_fragments_id_seq OWNED BY public.ia_fragments.id;


--
-- Name: invitations_inscription; Type: TABLE; Schema: public; Owner: ihm2026
--

CREATE TABLE public.invitations_inscription (
    id uuid NOT NULL,
    email_cible text NOT NULL,
    role_cible text NOT NULL,
    empreinte_pin text NOT NULL,
    expire_le timestamp with time zone NOT NULL,
    echecs integer DEFAULT 0 NOT NULL,
    utilise_le timestamp with time zone,
    cree_par bigint,
    cree_le timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT invitations_inscription_echecs_check CHECK (((echecs >= 0) AND (echecs <= 5))),
    CONSTRAINT invitations_inscription_role_cible_check CHECK ((role_cible = ANY (ARRAY['APPRENANT'::text, 'ENSEIGNANT'::text, 'ADMINISTRATEUR'::text])))
);


ALTER TABLE public.invitations_inscription OWNER TO ihm2026;

--
-- Name: journal_audit; Type: TABLE; Schema: public; Owner: ihm2026
--

CREATE TABLE public.journal_audit (
    id bigint NOT NULL,
    acteur_id bigint,
    action text NOT NULL,
    cible text,
    details jsonb,
    cree_le timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.journal_audit OWNER TO ihm2026;

--
-- Name: journal_audit_id_seq; Type: SEQUENCE; Schema: public; Owner: ihm2026
--

CREATE SEQUENCE public.journal_audit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.journal_audit_id_seq OWNER TO ihm2026;

--
-- Name: journal_audit_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ihm2026
--

ALTER SEQUENCE public.journal_audit_id_seq OWNED BY public.journal_audit.id;


--
-- Name: langages; Type: TABLE; Schema: public; Owner: ihm2026
--

CREATE TABLE public.langages (
    id bigint NOT NULL,
    code text NOT NULL,
    libelle text NOT NULL,
    version text NOT NULL,
    image_execution text NOT NULL
);


ALTER TABLE public.langages OWNER TO ihm2026;

--
-- Name: langages_id_seq; Type: SEQUENCE; Schema: public; Owner: ihm2026
--

CREATE SEQUENCE public.langages_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.langages_id_seq OWNER TO ihm2026;

--
-- Name: langages_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ihm2026
--

ALTER SEQUENCE public.langages_id_seq OWNED BY public.langages.id;


--
-- Name: niveaux; Type: TABLE; Schema: public; Owner: ihm2026
--

CREATE TABLE public.niveaux (
    id bigint NOT NULL,
    parcours_id bigint NOT NULL,
    rang integer NOT NULL,
    titre text NOT NULL,
    objectifs text,
    seuil integer NOT NULL,
    tentatives_max integer DEFAULT 3 NOT NULL,
    periode_debut date,
    periode_fin date,
    statut public.statut_contenu DEFAULT 'BROUILLON'::public.statut_contenu NOT NULL,
    cree_par bigint,
    cree_le timestamp with time zone DEFAULT now() NOT NULL,
    maj_le timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_periode CHECK (((periode_fin IS NULL) OR (periode_debut IS NULL) OR (periode_fin >= periode_debut))),
    CONSTRAINT niveaux_rang_check CHECK ((rang >= 1)),
    CONSTRAINT niveaux_seuil_check CHECK (((seuil >= 0) AND (seuil <= 20))),
    CONSTRAINT niveaux_tentatives_max_check CHECK ((tentatives_max >= 1))
);


ALTER TABLE public.niveaux OWNER TO ihm2026;

--
-- Name: niveaux_id_seq; Type: SEQUENCE; Schema: public; Owner: ihm2026
--

CREATE SEQUENCE public.niveaux_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.niveaux_id_seq OWNER TO ihm2026;

--
-- Name: niveaux_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ihm2026
--

ALTER SEQUENCE public.niveaux_id_seq OWNED BY public.niveaux.id;


--
-- Name: parcours; Type: TABLE; Schema: public; Owner: ihm2026
--

CREATE TABLE public.parcours (
    id bigint NOT NULL,
    langage_id bigint NOT NULL,
    libelle text NOT NULL,
    actif boolean DEFAULT true NOT NULL
);


ALTER TABLE public.parcours OWNER TO ihm2026;

--
-- Name: parcours_enseignants; Type: TABLE; Schema: public; Owner: ihm2026
--

CREATE TABLE public.parcours_enseignants (
    enseignant_id bigint NOT NULL,
    langage_id bigint NOT NULL,
    parcours_id bigint NOT NULL
);


ALTER TABLE public.parcours_enseignants OWNER TO ihm2026;

--
-- Name: parcours_id_seq; Type: SEQUENCE; Schema: public; Owner: ihm2026
--

CREATE SEQUENCE public.parcours_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.parcours_id_seq OWNER TO ihm2026;

--
-- Name: parcours_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ihm2026
--

ALTER SEQUENCE public.parcours_id_seq OWNED BY public.parcours.id;


--
-- Name: progressions; Type: TABLE; Schema: public; Owner: ihm2026
--

CREATE TABLE public.progressions (
    id bigint NOT NULL,
    apprenant_id bigint NOT NULL,
    niveau_id bigint NOT NULL,
    etat public.etat_niveau DEFAULT 'VERROUILLE'::public.etat_niveau NOT NULL,
    meilleure_note numeric(4,2),
    valide_le timestamp with time zone,
    maj_le timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.progressions OWNER TO ihm2026;

--
-- Name: progressions_id_seq; Type: SEQUENCE; Schema: public; Owner: ihm2026
--

CREATE SEQUENCE public.progressions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.progressions_id_seq OWNER TO ihm2026;

--
-- Name: progressions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ihm2026
--

ALTER SEQUENCE public.progressions_id_seq OWNED BY public.progressions.id;


--
-- Name: propositions; Type: TABLE; Schema: public; Owner: ihm2026
--

CREATE TABLE public.propositions (
    id bigint NOT NULL,
    question_id bigint NOT NULL,
    "position" integer NOT NULL,
    texte text NOT NULL,
    correcte boolean DEFAULT false NOT NULL
);


ALTER TABLE public.propositions OWNER TO ihm2026;

--
-- Name: propositions_id_seq; Type: SEQUENCE; Schema: public; Owner: ihm2026
--

CREATE SEQUENCE public.propositions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.propositions_id_seq OWNER TO ihm2026;

--
-- Name: propositions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ihm2026
--

ALTER SEQUENCE public.propositions_id_seq OWNED BY public.propositions.id;


--
-- Name: questions; Type: TABLE; Schema: public; Owner: ihm2026
--

CREATE TABLE public.questions (
    id bigint NOT NULL,
    test_id bigint NOT NULL,
    "position" integer NOT NULL,
    type public.type_question DEFAULT 'QCM'::public.type_question NOT NULL,
    enonce text NOT NULL,
    bareme numeric(4,2) DEFAULT 1.00 NOT NULL,
    active_pedagogique boolean DEFAULT true NOT NULL,
    CONSTRAINT questions_bareme_check CHECK ((bareme > (0)::numeric))
);


ALTER TABLE public.questions OWNER TO ihm2026;

--
-- Name: questions_id_seq; Type: SEQUENCE; Schema: public; Owner: ihm2026
--

CREATE SEQUENCE public.questions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.questions_id_seq OWNER TO ihm2026;

--
-- Name: questions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ihm2026
--

ALTER SEQUENCE public.questions_id_seq OWNED BY public.questions.id;


--
-- Name: reponses; Type: TABLE; Schema: public; Owner: ihm2026
--

CREATE TABLE public.reponses (
    id bigint NOT NULL,
    tentative_id bigint NOT NULL,
    question_id bigint NOT NULL,
    proposition_id bigint,
    texte_libre text,
    correcte boolean
);


ALTER TABLE public.reponses OWNER TO ihm2026;

--
-- Name: reponses_id_seq; Type: SEQUENCE; Schema: public; Owner: ihm2026
--

CREATE SEQUENCE public.reponses_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.reponses_id_seq OWNER TO ihm2026;

--
-- Name: reponses_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ihm2026
--

ALTER SEQUENCE public.reponses_id_seq OWNED BY public.reponses.id;


--
-- Name: ressources; Type: TABLE; Schema: public; Owner: ihm2026
--

CREATE TABLE public.ressources (
    id bigint NOT NULL,
    niveau_id bigint NOT NULL,
    libelle text NOT NULL,
    url text NOT NULL
);


ALTER TABLE public.ressources OWNER TO ihm2026;

--
-- Name: ressources_id_seq; Type: SEQUENCE; Schema: public; Owner: ihm2026
--

CREATE SEQUENCE public.ressources_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.ressources_id_seq OWNER TO ihm2026;

--
-- Name: ressources_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ihm2026
--

ALTER SEQUENCE public.ressources_id_seq OWNED BY public.ressources.id;


--
-- Name: sessions; Type: TABLE; Schema: public; Owner: ihm2026
--

CREATE TABLE public.sessions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    utilisateur_id bigint NOT NULL,
    empreinte_jeton text NOT NULL,
    expire_le timestamp with time zone NOT NULL,
    cree_le timestamp with time zone DEFAULT now() NOT NULL,
    derniere_activite timestamp with time zone DEFAULT now() NOT NULL,
    expiration_max timestamp with time zone NOT NULL
);


ALTER TABLE public.sessions OWNER TO ihm2026;

--
-- Name: soumissions_code; Type: TABLE; Schema: public; Owner: ihm2026
--

CREATE TABLE public.soumissions_code (
    id bigint NOT NULL,
    tentative_id bigint NOT NULL,
    langage text NOT NULL,
    source text NOT NULL,
    sortie text,
    erreur text,
    statut text DEFAULT 'EN_ATTENTE'::text NOT NULL,
    duree_ms integer,
    cree_le timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.soumissions_code OWNER TO ihm2026;

--
-- Name: soumissions_code_id_seq; Type: SEQUENCE; Schema: public; Owner: ihm2026
--

CREATE SEQUENCE public.soumissions_code_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.soumissions_code_id_seq OWNER TO ihm2026;

--
-- Name: soumissions_code_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ihm2026
--

ALTER SEQUENCE public.soumissions_code_id_seq OWNED BY public.soumissions_code.id;


--
-- Name: tentatives; Type: TABLE; Schema: public; Owner: ihm2026
--

CREATE TABLE public.tentatives (
    id bigint NOT NULL,
    apprenant_id bigint NOT NULL,
    test_id bigint NOT NULL,
    numero integer NOT NULL,
    statut public.statut_tentative DEFAULT 'EN_COURS'::public.statut_tentative NOT NULL,
    note numeric(4,2),
    commencee_le timestamp with time zone DEFAULT now() NOT NULL,
    soumise_le timestamp with time zone,
    enseignant_id bigint,
    sujet_snapshot jsonb,
    reponses_snapshot jsonb,
    code_source text,
    note_brouillon numeric(4,2),
    commentaire_brouillon text,
    decision_brouillon boolean,
    publie_le timestamp with time zone,
    seuil_snapshot integer,
    CONSTRAINT ck_note CHECK (((note IS NULL) OR ((note >= (0)::numeric) AND (note <= (20)::numeric)))),
    CONSTRAINT tentatives_note_brouillon_check CHECK (((note_brouillon >= (0)::numeric) AND (note_brouillon <= (20)::numeric))),
    CONSTRAINT tentatives_seuil_snapshot_check CHECK (((seuil_snapshot >= 0) AND (seuil_snapshot <= 20)))
);


ALTER TABLE public.tentatives OWNER TO ihm2026;

--
-- Name: tentatives_authentification; Type: TABLE; Schema: public; Owner: ihm2026
--

CREATE TABLE public.tentatives_authentification (
    email_normalise text NOT NULL,
    empreinte_source text NOT NULL,
    debut_fenetre timestamp with time zone DEFAULT now() NOT NULL,
    echecs integer DEFAULT 0 NOT NULL,
    CONSTRAINT tentatives_authentification_echecs_check CHECK ((echecs >= 0))
);


ALTER TABLE public.tentatives_authentification OWNER TO ihm2026;

--
-- Name: tentatives_id_seq; Type: SEQUENCE; Schema: public; Owner: ihm2026
--

CREATE SEQUENCE public.tentatives_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.tentatives_id_seq OWNER TO ihm2026;

--
-- Name: tentatives_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ihm2026
--

ALTER SEQUENCE public.tentatives_id_seq OWNED BY public.tentatives.id;


--
-- Name: tests; Type: TABLE; Schema: public; Owner: ihm2026
--

CREATE TABLE public.tests (
    id bigint NOT NULL,
    niveau_id bigint NOT NULL,
    duree_minutes integer DEFAULT 45 NOT NULL,
    ordre_aleatoire boolean DEFAULT false NOT NULL,
    CONSTRAINT tests_duree_minutes_check CHECK ((duree_minutes > 0))
);


ALTER TABLE public.tests OWNER TO ihm2026;

--
-- Name: tests_id_seq; Type: SEQUENCE; Schema: public; Owner: ihm2026
--

CREATE SEQUENCE public.tests_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.tests_id_seq OWNER TO ihm2026;

--
-- Name: tests_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ihm2026
--

ALTER SEQUENCE public.tests_id_seq OWNED BY public.tests.id;


--
-- Name: utilisateurs; Type: TABLE; Schema: public; Owner: ihm2026
--

CREATE TABLE public.utilisateurs (
    id bigint NOT NULL,
    nom text NOT NULL,
    email text NOT NULL,
    mot_de_passe text NOT NULL,
    role public.role_utilisateur NOT NULL,
    actif boolean DEFAULT true NOT NULL,
    cree_le timestamp with time zone DEFAULT now() NOT NULL,
    derniere_connexion timestamp with time zone,
    statut_inscription text DEFAULT 'EN_ATTENTE'::text NOT NULL,
    CONSTRAINT ck_email CHECK ((email ~~ '%@%.%'::text)),
    CONSTRAINT ck_utilisateurs_statut_inscription CHECK ((statut_inscription = ANY (ARRAY['EN_ATTENTE'::text, 'ACCEPTE'::text, 'REFUSE'::text])))
);


ALTER TABLE public.utilisateurs OWNER TO ihm2026;

--
-- Name: utilisateurs_id_seq; Type: SEQUENCE; Schema: public; Owner: ihm2026
--

CREATE SEQUENCE public.utilisateurs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.utilisateurs_id_seq OWNER TO ihm2026;

--
-- Name: utilisateurs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: ihm2026
--

ALTER SEQUENCE public.utilisateurs_id_seq OWNED BY public.utilisateurs.id;


--
-- Name: agent_ia id; Type: DEFAULT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.agent_ia ALTER COLUMN id SET DEFAULT nextval('public.agent_ia_id_seq'::regclass);


--
-- Name: aides_ia id; Type: DEFAULT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.aides_ia ALTER COLUMN id SET DEFAULT nextval('public.aides_ia_id_seq'::regclass);


--
-- Name: blocs id; Type: DEFAULT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.blocs ALTER COLUMN id SET DEFAULT nextval('public.blocs_id_seq'::regclass);


--
-- Name: cours id; Type: DEFAULT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.cours ALTER COLUMN id SET DEFAULT nextval('public.cours_id_seq'::regclass);


--
-- Name: entrainements_niveaux id; Type: DEFAULT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.entrainements_niveaux ALTER COLUMN id SET DEFAULT nextval('public.entrainements_niveaux_id_seq'::regclass);


--
-- Name: ia_fragments id; Type: DEFAULT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.ia_fragments ALTER COLUMN id SET DEFAULT nextval('public.ia_fragments_id_seq'::regclass);


--
-- Name: journal_audit id; Type: DEFAULT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.journal_audit ALTER COLUMN id SET DEFAULT nextval('public.journal_audit_id_seq'::regclass);


--
-- Name: langages id; Type: DEFAULT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.langages ALTER COLUMN id SET DEFAULT nextval('public.langages_id_seq'::regclass);


--
-- Name: niveaux id; Type: DEFAULT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.niveaux ALTER COLUMN id SET DEFAULT nextval('public.niveaux_id_seq'::regclass);


--
-- Name: parcours id; Type: DEFAULT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.parcours ALTER COLUMN id SET DEFAULT nextval('public.parcours_id_seq'::regclass);


--
-- Name: progressions id; Type: DEFAULT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.progressions ALTER COLUMN id SET DEFAULT nextval('public.progressions_id_seq'::regclass);


--
-- Name: propositions id; Type: DEFAULT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.propositions ALTER COLUMN id SET DEFAULT nextval('public.propositions_id_seq'::regclass);


--
-- Name: questions id; Type: DEFAULT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.questions ALTER COLUMN id SET DEFAULT nextval('public.questions_id_seq'::regclass);


--
-- Name: reponses id; Type: DEFAULT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.reponses ALTER COLUMN id SET DEFAULT nextval('public.reponses_id_seq'::regclass);


--
-- Name: ressources id; Type: DEFAULT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.ressources ALTER COLUMN id SET DEFAULT nextval('public.ressources_id_seq'::regclass);


--
-- Name: soumissions_code id; Type: DEFAULT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.soumissions_code ALTER COLUMN id SET DEFAULT nextval('public.soumissions_code_id_seq'::regclass);


--
-- Name: tentatives id; Type: DEFAULT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.tentatives ALTER COLUMN id SET DEFAULT nextval('public.tentatives_id_seq'::regclass);


--
-- Name: tests id; Type: DEFAULT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.tests ALTER COLUMN id SET DEFAULT nextval('public.tests_id_seq'::regclass);


--
-- Name: utilisateurs id; Type: DEFAULT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.utilisateurs ALTER COLUMN id SET DEFAULT nextval('public.utilisateurs_id_seq'::regclass);


--
-- Data for Name: affectations_pedagogiques; Type: TABLE DATA; Schema: public; Owner: ihm2026
--

COPY public.affectations_pedagogiques (apprenant_id, enseignant_id, affecte_par, maj_le) FROM stdin;
72	73	1	2026-11-01 19:45:15.384042+01
3	76	1	2026-11-02 12:15:10.447207+01
\.


--
-- Data for Name: agent_ia; Type: TABLE DATA; Schema: public; Owner: ihm2026
--

COPY public.agent_ia (id, nom, actif, positionnement, quota_jour, cree_le) FROM stdin;
1	agent_ia	t	A	200	2026-10-28 21:21:12.527846+01
\.


--
-- Data for Name: aides_ia; Type: TABLE DATA; Schema: public; Owner: ihm2026
--

COPY public.aides_ia (id, apprenant_id, tentative_id, agent_id, question, reponse, modele, cree_le) FROM stdin;
\.


--
-- Data for Name: blocs; Type: TABLE DATA; Schema: public; Owner: ihm2026
--

COPY public.blocs (id, cours_id, "position", type, langage, contenu) FROM stdin;
1	1	1	TEXTE	\N	Une fonction regroupe des instructions réutilisables. En Python, elle se déclare avec le mot-clé def et son corps est indenté : l'indentation fait partie de la syntaxe.
2	1	2	CODE	python	def moyenne(notes):\n    """Retourne la moyenne d'une liste de notes."""\n    if not notes:\n        return 0\n    return sum(notes) / len(notes)\n\nprint(moyenne([12, 15, 9]))   # 12.0
3	1	3	TEXTE	\N	Les paramètres peuvent avoir une valeur par défaut : def saluer(nom="monde"). Attention, la valeur par défaut est évaluée une seule fois, à la définition de la fonction.
4	1	4	CODE	python	def saluer(nom: str = "monde") -> str:\n    return f"Bonjour, {nom} !"\n\nprint(saluer())        # Bonjour, monde !\nprint(saluer("Amina")) # Bonjour, Amina !
5	2	1	TEXTE	\N	Une méthode regroupe des instructions et porte un type de retour. Si le type de retour est void, la méthode ne renvoie rien ; sinon, tous les chemins d'exécution doivent atteindre un return, faute de quoi le compilateur refuse la compilation.
6	2	2	CODE	java	public static double moyenne(int[] notes) {\n    if (notes.length == 0) {\n        return 0;\n    }\n    int somme = 0;\n    for (int n : notes) {\n        somme += n;\n    }\n    return (double) somme / notes.length;\n}
7	2	3	TEXTE	\N	En Java, un tableau connaît sa taille avec length (sans parenthèses), alors qu'une chaîne utilise length(). Confondre les deux est l'erreur de compilation la plus fréquente au niveau 3.
8	3	1	TEXTE	\N	En C++, le type de retour est déclaré avant le nom de la fonction. Une fonction déclarée sans type de retour valide ne compile pas ; le standard C++ impose que toute fonction non void retourne une valeur sur chaque chemin, sinon le comportement est indéfini.
9	3	2	CODE	cpp	#include <vector>\n\ndouble moyenne(const std::vector<int>& notes) {\n    if (notes.empty()) {\n        return 0.0;\n    }\n    int somme = 0;\n    for (int n : notes) {\n        somme += n;\n    }\n    return static_cast<double>(somme) / notes.size();\n}
10	3	3	TEXTE	\N	La division entre deux entiers donne un entier : il faut convertir explicitement avec static_cast<double> avant de diviser, sinon 7 / 2 vaut 3 et non 3.5.
11	1	5	TEXTE	\N	Une fonction qui ne contient aucun return, ou dont le return est sans valeur, renvoie None : c'est la valeur de retour par défaut en Python. Pour renvoyer un résultat, écrivez return suivi de la valeur, et veillez à ce que tous les chemins d'exécution atteignent un return.
12	4	11	TEXTE	\N	En Python, une variable est un nom associé à un objet : aucune déclaration de type n'est nécessaire, le type est porté par la valeur. Les types de base sont int pour les entiers, float pour les réels, str pour les chaînes et bool pour les booléens. Attention à la division : 7 / 2 donne 3.5, alors que 7 // 2 donne 3.
13	4	21	CODE	python	age = 20            # int\nmoyenne = 12.5      # float\nnom = "Amina"       # str\nreussi = moyenne >= 10   # bool\n\nprint(type(age))      # <class 'int'>\nprint(7 / 2)          # 3.5\nprint(7 // 2)         # 3\nprint(int("12") + 1)  # conversion explicite : 13
14	5	11	TEXTE	\N	En Java, toute variable a un type déclaré avant son nom : int pour les entiers, double pour les réels, String pour les chaînes, boolean pour les booléens. Le type est fixé à la compilation et ne change jamais. La division de deux int est entière : 7 / 2 vaut 3 ; pour obtenir un réel, l'un des deux opérandes doit être de type double.
15	5	21	CODE	java	int age = 20;\ndouble moyenne = 12.5;\nString nom = "Amina";\nboolean reussi = moyenne >= 10;\n\nSystem.out.println(7 / 2);            // 3\nSystem.out.println(7 / 2.0);          // 3.5\nSystem.out.println(Integer.parseInt("12") + 1);  // 13
16	6	21	CODE	cpp	#include <string>\n\nint age = 20;\ndouble moyenne = 12.5;\nstd::string nom = "Amina";\nbool reussi = moyenne >= 10;\n\nstd::cout << 7 / 2 << std::endl;                       // 3\nstd::cout << static_cast<double>(7) / 2 << std::endl;  // 3.5
17	6	11	TEXTE	\N	En C++, le type précède le nom de la variable et sa taille dépend de la plateforme. Les types usuels sont int, double, bool et std::string, qui exige #include <string>. La division entre deux entiers est entière : 7 / 2 vaut 3. Une conversion explicite s'écrit avec static_cast<double>, plus sûre qu'un transtypage à la C.
18	7	21	CODE	python	note = 13\n\nif note >= 16:\n    mention = "très bien"\nelif note >= 12:\n    mention = "bien"\nelse:\n    mention = "à revoir"\n\nprint(mention)   # bien
19	7	11	TEXTE	\N	Une condition s'écrit avec if, elif et else, suivie de deux-points. Contrairement à Java et C++, les parenthèses ne sont pas nécessaires et le bloc est délimité par l'indentation : mélanger tabulations et espaces provoque une IndentationError. La comparaison d'égalité utilise deux signes égaux, l'affectation un seul.
20	8	11	TEXTE	\N	En Java, la condition d'un if est une expression booléenne entre parenthèses : il n'existe pas de conversion automatique d'un entier vers un booléen, écrire if (note) ne compile pas. Les blocs sont délimités par des accolades ; sans accolades, seule l'instruction suivante appartient au if, ce qui est une source classique d'erreur.
21	8	21	CODE	java	int note = 13;\nString mention;\n\nif (note >= 16) {\n    mention = "très bien";\n} else if (note >= 12) {\n    mention = "bien";\n} else {\n    mention = "à revoir";\n}\n\nSystem.out.println(mention);   // bien
22	9	21	CODE	cpp	int note = 13;\nstd::string mention;\n\nif (note >= 16) {\n    mention = "très bien";\n} else if (note >= 12) {\n    mention = "bien";\n} else {\n    mention = "à revoir";\n}\n\nstd::cout << mention << std::endl;   // bien
23	9	11	TEXTE	\N	En C++, la condition d'un if est convertie en booléen : un entier nul vaut faux, tout autre entier vaut vrai. Cette souplesse est un piège : la faute de frappe if (x = 5), qui affecte au lieu de comparer, compile sans avertissement et rend la condition toujours vraie. Les compilateurs modernes la signalent avec l'option -Wall.
24	10	21	CODE	python	notes = [12, 15, 9]\nsomme = 0\nfor note in notes:\n    somme += note\nprint(somme / len(notes))   # 12.0\n\ni = 0\nwhile i < 3:\n    i += 1          # sans cette ligne : boucle infinie\nprint(i)            # 3
25	10	11	TEXTE	\N	La boucle for parcourt directement les éléments d'une séquence, sans compteur ni condition d'arrêt, comme dans for note in notes. Pour répéter un nombre connu de fois, on utilise range(n), qui produit les entiers de 0 à n-1. La boucle while répète tant qu'une condition reste vraie ; si cette condition ne devient jamais fausse, le programme ne se termine pas.
26	11	21	CODE	java	int[] notes = {12, 15, 9};\nint somme = 0;\n\nfor (int note : notes) {\n    somme += note;\n}\nSystem.out.println((double) somme / notes.length);   // 12.0\n\nfor (int i = 0; i < notes.length; i++) {\n    System.out.print(notes[i] + " ");\n}
27	11	11	TEXTE	\N	Java propose trois formes : le for classique avec initialisation, condition et incrément ; le for-each pour parcourir un tableau sans indice ; et le while pour une condition inconnue à l'avance. Le parcours par indice impose de rester dans les bornes : un tableau de taille n se parcourt de 0 à n-1, sinon une ArrayIndexOutOfBoundsException est levée.
28	12	21	CODE	cpp	#include <vector>\n\nstd::vector<int> notes{12, 15, 9};\nint somme = 0;\n\nfor (int n : notes) {\n    somme += n;\n}\nstd::cout << static_cast<double>(somme) / notes.size() << std::endl;  // 12\n\nfor (int& n : notes) {\n    n += 1;      // modifie le vecteur : passage par référence\n}
29	12	11	TEXTE	\N	C++ reprend les trois formes de Java : for classique, for sur intervalle et while. Le for sur intervalle s'écrit for (int n : notes) et parcourt une copie de chaque élément ; pour modifier le contenu, on écrit for (int& n : notes). Comme en Java, un indice hors bornes n'est pas vérifié à l'exécution : le comportement est indéfini.
30	13	11	TEXTE	\N	Une fonction se déclare avec le mot-clé def et son corps est indenté. Une fonction qui ne contient aucun return, ou dont le return est sans valeur, renvoie None : c'est la valeur de retour par défaut en Python. Pour renvoyer un résultat, écrivez return suivi de la valeur, et veillez à ce que tous les chemins d'exécution atteignent un return.
31	13	21	CODE	python	def moyenne(notes):\n    """Retourne la moyenne d'une liste de notes."""\n    if not notes:\n        return 0\n    return sum(notes) / len(notes)\n\ndef sans_retour(notes):\n    total = sum(notes)          # aucun return\n\nprint(moyenne([12, 15, 9]))     # 12.0\nprint(sans_retour([12, 15, 9])) # None
32	14	21	CODE	java	public static double moyenne(int[] notes) {\n    if (notes.length == 0) {\n        return 0;\n    }\n    int somme = 0;\n    for (int n : notes) {\n        somme += n;\n    }\n    return (double) somme / notes.length;\n}\n\n// moyenne(new int[]{12, 15, 9}) vaut 12.0
33	14	11	TEXTE	\N	Une méthode déclare son type de retour avant son nom. Si ce type est void, la méthode ne renvoie rien ; sinon, tous les chemins d'exécution doivent atteindre un return, faute de quoi le compilateur refuse la compilation. Une méthode peut aussi lever une exception plutôt que renvoyer une valeur en cas d'erreur.
34	15	11	TEXTE	\N	En C++, le type de retour est déclaré avant le nom de la fonction. Toute fonction non void doit retourner une valeur sur chaque chemin, sinon le comportement est indéfini. C++ autorise la surcharge : deux fonctions peuvent porter le même nom si leurs paramètres diffèrent, ce qui n'est pas possible en Python. Enfin, un paramètre volumineux se passe par référence constante (const std::vector<int>&) pour éviter une copie à chaque appel.
35	15	21	CODE	cpp	#include <vector>\n\ndouble moyenne(const std::vector<int>& notes) {\n    if (notes.empty()) {\n        return 0.0;\n    }\n    int somme = 0;\n    for (int n : notes) {\n        somme += n;\n    }\n    return static_cast<double>(somme) / notes.size();\n}\n\n// surcharge : même nom, paramètres différents\nint moyenne(int a, int b) {\n    return (a + b) / 2;\n}
36	16	11	TEXTE	\N	La liste est la collection ordonnée et modifiable de Python : elle s'écrit entre crochets et accepte des éléments de types différents. Sa taille s'obtient avec len(liste) et l'accès par indice commence à 0. Le dictionnaire associe des clés à des valeurs avec la syntaxe {clé: valeur} ; l'accès à une clé absente avec crochets lève une KeyError, alors que get renvoie None.
37	16	21	CODE	python	notes = [12, 15, 9]\nnotes.append(11)          # ajout en fin\nprint(len(notes))         # 4\nprint(notes[0])           # 12\n\nbulletin = {"Amina": 16, "Karim": 9}\nprint(bulletin.get("Lina"))   # None, pas de KeyError\nfor nom, note in bulletin.items():\n    print(nom, note)
38	17	21	CODE	java	import java.util.*;\n\nList<Integer> notes = new ArrayList<>(List.of(12, 15, 9));\nnotes.add(11);\nSystem.out.println(notes.size());      // 4\nSystem.out.println(notes.get(0));      // 12\n\nMap<String, Integer> bulletin = new HashMap<>();\nbulletin.put("Amina", 16);\nSystem.out.println(bulletin.getOrDefault("Lina", 0));   // 0\n\nint[] tableau = {12, 15, 9};\nSystem.out.println(tableau.length);    // 3, sans parenthèses
39	17	11	TEXTE	\N	En Java, la taille d'un tableau est fixée à sa création et s'obtient avec length, sans parenthèses. Pour une taille variable, on utilise les collections : ArrayList pour une liste modifiable, HashMap pour un dictionnaire. Leur taille s'obtient avec size(), avec des parenthèses. Confondre length et size() est l'erreur de compilation la plus fréquente.
40	18	21	CODE	cpp	#include <vector>\n#include <map>\n\nstd::vector<int> notes{12, 15, 9};\nnotes.push_back(11);\nstd::cout << notes.size() << std::endl;   // 4\nstd::cout << notes.at(0) << std::endl;    // 12, avec vérification\n\nstd::map<std::string, int> bulletin;\nbulletin["Amina"] = 16;\nstd::cout << bulletin.size() << std::endl;  // 1
41	18	11	TEXTE	\N	La bibliothèque standard de C++ fournit std::vector pour un tableau redimensionnable et std::map pour un dictionnaire ordonné. Leur taille s'obtient avec size(). L'accès par crochets ne vérifie pas les bornes : utiliser at() lève une exception si l'indice est hors limites. Un std::vector se passe par référence constante pour éviter une copie coûteuse.
42	19	21	CODE	python	notes = []\n\ntry:\n    moyenne = sum(notes) / len(notes)\nexcept ZeroDivisionError:\n    moyenne = 0     # on traite le cas, on ne masque pas l'erreur\n\nprint(moyenne)      # 0\n\n# IndexError : notes[0] lèverait une exception
43	19	11	TEXTE	\N	Python distingue les erreurs de syntaxe, détectées avant l'exécution, des exceptions, déclenchées pendant l'exécution. Une exception se capture avec try et except ; capturer une exception trop large masque les fautes de programmation. La division par zéro lève ZeroDivisionError, un indice hors bornes lève IndexError.
44	20	11	TEXTE	\N	En Java, une exception se capture avec try et catch, et le bloc finally s'exécute dans tous les cas. Les exceptions vérifiées, comme IOException, doivent être déclarées ou capturées : le compilateur l'impose. Les exceptions non vérifiées, comme NullPointerException ou ArrayIndexOutOfBoundsException, traduisent une faute de programmation et ne devraient pas être capturées à la légère.
45	20	21	CODE	java	int[] notes = new int[0];\n\ntry {\n    System.out.println(notes[0]);\n} catch (ArrayIndexOutOfBoundsException e) {\n    System.out.println("indice hors bornes : " + e.getMessage());\n} finally {\n    System.out.println("fin du traitement");\n}
46	21	11	TEXTE	\N	C++ utilise try et catch, mais rien n'oblige à traiter une exception : une exception non capturée termine le programme. Le conteneur std::vector lève std::out_of_range si l'on utilise at() avec un indice invalide, alors que l'opérateur crochets ne vérifie rien. Le RAII garantit que les ressources sont libérées même en cas d'exception.
47	21	21	CODE	cpp	#include <stdexcept>\n#include <vector>\n\nstd::vector<int> notes;\n\ntry {\n    std::cout << notes.at(0) << std::endl;\n} catch (const std::out_of_range& e) {\n    std::cout << "indice hors bornes : " << e.what() << std::endl;\n}
48	22	1	TEXTE	\N	Les variables et les types en Python\n\nObjectif : créer des variables, reconnaître les types de base et afficher leurs valeurs.\n\n1. Qu’est-ce qu’une variable ?\nUne variable associe un nom à une valeur. En Python, on utilise le signe = pour affecter une valeur.\n\nExemples :\nprenom = "Amina"\nage = 20\nmoyenne = 14.5\nest_inscrit = True\n\n2. Les types de base\n- str : une chaîne de caractères, comme "Amina".\n- int : un nombre entier, comme 20.\n- float : un nombre décimal, comme 14.5. En Python, on utilise un point, pas une virgule.\n- bool : une valeur logique, True ou False, avec une majuscule.\n\nPython détermine le type à partir de la valeur affectée.\n\n3. Afficher une valeur\nLa fonction print() affiche du texte ou la valeur d’une variable.\n\nExemples :\nprint(prenom)\nprint("Âge :", age)\n\nPour connaître le type d’une valeur :\nprint(type(age))\n\n4. Modifier une variable\nUne variable peut recevoir une nouvelle valeur.\n\nExemple :\nage = 20\nage = age + 1\nprint(age)\n\nLe résultat affiché est 21.\n\n5. Règles de nommage\nUn nom de variable ne commence pas par un chiffre. Il ne contient pas d’espace. Les majuscules et minuscules sont différentes : age et Age sont deux noms distincts.\n\nExemples conseillés :\nprenom\nage\nmoyenne_generale\nest_inscrit\n\nÀ retenir :\n= sert à affecter une valeur.\nLes chaînes de caractères sont entre guillemets.\nLes nombres et les booléens ne nécessitent pas de guillemets.
49	23	1	TEXTE	\N	Conditions et comparaisons en Python\n\nObjectifs :\n- comparer deux valeurs ;\n- utiliser if, elif et else ;\n- combiner des conditions ;\n- respecter l'indentation.\n\n1. Les comparaisons\n\nUne comparaison donne un booléen : True ou False.\n\n== : égal à\n!= : différent de\n> : supérieur à\n< : inférieur à\n>= : supérieur ou égal à\n<= : inférieur ou égal à\n\nExemples :\nprint(8 > 3)       # True\nprint(8 == 3)      # False\nprint(10 >= 10)    # True\n\nAttention :\n= sert à affecter une valeur.\n== sert à comparer deux valeurs.\n\n2. Choisir entre deux actions\n\nif exécute un bloc lorsque sa condition est vraie.\nelse exécute un autre bloc lorsque la condition est fausse.\n\nExemple :\nage = 18\n\nif age >= 18:\n    print("Majeur")\nelse:\n    print("Mineur")\n\nLes deux-points sont obligatoires.\nLes instructions à l'intérieur des blocs sont indentées, généralement avec quatre espaces.\n\n3. Tester plusieurs cas\n\nelif permet d'ajouter une condition.\n\nExemple :\ntemperature = 22\n\nif temperature < 10:\n    print("Froid")\nelif temperature < 25:\n    print("Tempéré")\nelse:\n    print("Chaud")\n\nLe programme teste les conditions dans l'ordre.\nSeul le premier bloc dont la condition est vraie est exécuté.\nSi aucune condition n'est vraie, le bloc else est exécuté.\n\n4. Combiner des conditions\n\nand : les deux conditions doivent être vraies.\nor : au moins une condition doit être vraie.\nnot : inverse un booléen.\n\nExemple :\nage = 20\nest_inscrit = True\n\nif age >= 18 and est_inscrit:\n    print("Accès autorisé")\n\n5. Vérifier un intervalle\n\nPour vérifier qu'une note est comprise entre 0 et 20 :\n\nnote = 15\n\nif 0 <= note <= 20:\n    print("Note valide")\nelse:\n    print("Note invalide")\n\nÀ retenir :\n- Utiliser == pour comparer.\n- Mettre les deux-points après if, elif et else.\n- Respecter l'indentation.\n- Vérifier les valeurs aux limites des intervalles.
50	24	1	TEXTE	\N	Conditions et comparaisons en Python\n\nObjectifs :\n- comparer deux valeurs ;\n- utiliser if, elif et else ;\n- combiner des conditions ;\n- respecter l'indentation.\n\n1. Les comparaisons\n\nUne comparaison donne un booléen : True ou False.\n\n== : égal à\n!= : différent de\n> : supérieur à\n< : inférieur à\n>= : supérieur ou égal à\n<= : inférieur ou égal à\n\nExemples :\nprint(8 > 3)       # True\nprint(8 == 3)      # False\nprint(10 >= 10)    # True\n\nAttention :\n= sert à affecter une valeur.\n== sert à comparer deux valeurs.\n\n2. Choisir entre deux actions\n\nif exécute un bloc lorsque sa condition est vraie.\nelse exécute un autre bloc lorsque la condition est fausse.\n\nExemple :\nage = 18\n\nif age >= 18:\n    print("Majeur")\nelse:\n    print("Mineur")\n\nLes deux-points sont obligatoires.\nLes instructions à l'intérieur des blocs sont indentées, généralement avec quatre espaces.\n\n3. Tester plusieurs cas\n\nelif permet d'ajouter une condition.\n\nExemple :\ntemperature = 22\n\nif temperature < 10:\n    print("Froid")\nelif temperature < 25:\n    print("Tempéré")\nelse:\n    print("Chaud")\n\nLe programme teste les conditions dans l'ordre.\nSeul le premier bloc dont la condition est vraie est exécuté.\nSi aucune condition n'est vraie, le bloc else est exécuté.\n\n4. Combiner des conditions\n\nand : les deux conditions doivent être vraies.\nor : au moins une condition doit être vraie.\nnot : inverse un booléen.\n\nExemple :\nage = 20\nest_inscrit = True\n\nif age >= 18 and est_inscrit:\n    print("Accès autorisé")\n\n5. Vérifier un intervalle\n\nPour vérifier qu'une note est comprise entre 0 et 20 :\n\nnote = 15\n\nif 0 <= note <= 20:\n    print("Note valide")\nelse:\n    print("Note invalide")\n\nÀ retenir :\n- Utiliser == pour comparer.\n- Mettre les deux-points après if, elif et else.\n- Respecter l'indentation.\n- Vérifier les valeurs aux limites des intervalles.
\.


--
-- Data for Name: codes_connexion; Type: TABLE DATA; Schema: public; Owner: ihm2026
--

COPY public.codes_connexion (identifiant, utilisateur_id, empreinte_code, expire_le, echecs, cree_le) FROM stdin;
\.


--
-- Data for Name: cours; Type: TABLE DATA; Schema: public; Owner: ihm2026
--

COPY public.cours (id, niveau_id, titre, statut, maj_le) FROM stdin;
4	1	Variables et types en Python	PUBLIE	2026-10-28 21:21:15.090948+01
5	6	Variables et types en Java	PUBLIE	2026-10-28 21:21:15.090948+01
6	11	Variables et types en C++	PUBLIE	2026-10-28 21:21:15.090948+01
7	1	Conditions en Python	PUBLIE	2026-10-28 21:21:15.090948+01
8	6	Conditions en Java	PUBLIE	2026-10-28 21:21:15.090948+01
9	11	Conditions en C++	PUBLIE	2026-10-28 21:21:15.090948+01
10	1	Boucles en Python	PUBLIE	2026-10-28 21:21:15.090948+01
11	6	Boucles en Java	PUBLIE	2026-10-28 21:21:15.090948+01
12	11	Boucles en C++	PUBLIE	2026-10-28 21:21:15.090948+01
13	1	Fonctions et valeur de retour en Python	PUBLIE	2026-10-28 21:21:15.090948+01
14	6	Méthodes et valeur de retour en Java	PUBLIE	2026-10-28 21:21:15.090948+01
15	11	Fonctions, retour et surcharge en C++	PUBLIE	2026-10-28 21:21:15.090948+01
16	1	Listes et dictionnaires en Python	PUBLIE	2026-10-28 21:21:15.090948+01
17	6	Collections en Java	PUBLIE	2026-10-28 21:21:15.090948+01
18	11	Conteneurs STL en C++	PUBLIE	2026-10-28 21:21:15.090948+01
19	1	Erreurs et exceptions en Python	PUBLIE	2026-10-28 21:21:15.090948+01
20	6	Exceptions en Java	PUBLIE	2026-10-28 21:21:15.090948+01
21	11	Exceptions en C++	PUBLIE	2026-10-28 21:21:15.090948+01
1	3	Fonctions et modules	BROUILLON	2026-10-28 21:21:15.090948+01
2	6	Méthodes et valeur de retour	BROUILLON	2026-10-28 21:21:15.090948+01
3	11	Fonctions, retour et surcharge	BROUILLON	2026-10-28 21:21:15.090948+01
22	16	Variable et Types	PUBLIE	2026-11-01 20:44:10.411433+01
23	17	Conditions et comparaisons	PUBLIE	2026-11-02 12:18:58.639201+01
24	18	Conditions et comparaisons	PUBLIE	2026-11-02 16:25:05.652408+01
\.


--
-- Data for Name: entrainements_niveaux; Type: TABLE DATA; Schema: public; Owner: ihm2026
--

COPY public.entrainements_niveaux (id, niveau_id, enonce, code_depart, publie, cree_par, maj_le) FROM stdin;
1	16	Exercice : présenter un apprenant\n\nCrée un programme Python qui :\n\n1. Déclare une variable prenom contenant une chaîne de caractères.\n2. Déclare une variable age contenant un nombre entier.\n3. Déclare une variable moyenne contenant un nombre décimal.\n4. Déclare une variable est_inscrit contenant un booléen.\n5. Affiche les quatre valeurs avec print().\n6. Affiche le type de chaque variable avec type().\n7. Augmente age de 1, puis affiche le nouvel âge.\n\nVérifie que :\n- le prénom est entre guillemets ;\n- la moyenne utilise un point décimal ;\n- le booléen est True ou False, sans guillemets.\n\nL’aide IA est autorisée pendant cet entraînement. Essaie d’abord, puis demande un indice si tu bloques.\n\nCet espace sert à rédiger ton code et à demander de l’aide : il ne l’exécute pas automatiquement.	# 1. Complète les valeurs ci-dessous.\n# None est une valeur temporaire à remplacer.\n\nprenom = None\nage = None\nmoyenne = None\nest_inscrit = None\n\n# 2. Affiche les valeurs avec print().\n\n\n# 3. Affiche le type de chaque variable avec type().\n\n\n# 4. Augmente age de 1 et affiche le nouvel âge.	t	73	2026-11-01 20:44:10.411433+01
2	17	Exercice : classer une note\n\nÉcris un programme Python utilisant une variable note.\n\nLe programme doit afficher :\n- "Note invalide" si la note est inférieure à 0 ou supérieure à 20 ;\n- "À retravailler" si la note est valide et inférieure à 10 ;\n- "Admis" si la note est supérieure ou égale à 10 et inférieure à 16 ;\n- "Très bien" si la note est supérieure ou égale à 16 et inférieure ou égale à 20.\n\nConsignes :\n1. Vérifie d'abord si la note est invalide.\n2. Utilise if, elif et else.\n3. Affiche le message avec print().\n4. Respecte les deux-points et l'indentation.\n\nVérifie ton raisonnement avec ces valeurs :\n- -1 : Note invalide\n- 0 : À retravailler\n- 9.5 : À retravailler\n- 10 : Admis\n- 15.5 : Admis\n- 16 : Très bien\n- 20 : Très bien\n- 21 : Note invalide\n\nL'aide IA est autorisée pendant cet entraînement.\nEssaie d'abord seul, puis demande un indice si nécessaire.\n\nL'éditeur de la plateforme n'exécute pas automatiquement le code.	note = 12\n	t	76	2026-11-02 12:18:58.639201+01
3	18	Exercice : classer une note\n\nÉcris un programme Python utilisant une variable note.\n\nLe programme doit afficher :\n- "Note invalide" si la note est inférieure à 0 ou supérieure à 20 ;\n- "À retravailler" si la note est valide et inférieure à 10 ;\n- "Admis" si la note est supérieure ou égale à 10 et inférieure à 16 ;\n- "Très bien" si la note est supérieure ou égale à 16 et inférieure ou égale à 20.\n\nConsignes :\n1. Vérifie d'abord si la note est invalide.\n2. Utilise if, elif et else.\n3. Affiche le message avec print().\n4. Respecte les deux-points et l'indentation.\n\nVérifie ton raisonnement avec ces valeurs :\n- -1 : Note invalide\n- 0 : À retravailler\n- 9.5 : À retravailler\n- 10 : Admis\n- 15.5 : Admis\n- 16 : Très bien\n- 20 : Très bien\n- 21 : Note invalide\n\nL'aide IA est autorisée pendant cet entraînement.\nEssaie d'abord seul, puis demande un indice si nécessaire.\n\nL'éditeur de la plateforme n'exécute pas automatiquement le code.	note = 12\n	t	73	2026-11-02 16:25:05.652408+01
\.


--
-- Data for Name: ia_fragments; Type: TABLE DATA; Schema: public; Owner: ihm2026
--

COPY public.ia_fragments (id, niveau_id, cours_id, langage, section, ordre, contenu, caracteres, vecteur) FROM stdin;
12	1	4	python	Variables et types en Python — Variables et types	11	En Python, une variable est un nom associé à un objet : aucune déclaration de type n'est nécessaire, le type est porté par la valeur. Les types de base sont int pour les entiers, float pour les réels, str pour les chaînes et bool pour les booléens. Attention à la division : 7 / 2 donne 3.5, alors que 7 // 2 donne 3.	317	\N
13	1	4	python	Variables et types en Python — Variables et types	21	age = 20            # int\nmoyenne = 12.5      # float\nnom = "Amina"       # str\nreussi = moyenne >= 10   # bool\n\nprint(type(age))      # <class 'int'>\nprint(7 / 2)          # 3.5\nprint(7 // 2)         # 3\nprint(int("12") + 1)  # conversion explicite : 13	254	\N
14	6	5	java	Variables et types en Java — Classes et objets	11	En Java, toute variable a un type déclaré avant son nom : int pour les entiers, double pour les réels, String pour les chaînes, boolean pour les booléens. Le type est fixé à la compilation et ne change jamais. La division de deux int est entière : 7 / 2 vaut 3 ; pour obtenir un réel, l'un des deux opérandes doit être de type double.	334	\N
15	6	5	java	Variables et types en Java — Classes et objets	21	int age = 20;\ndouble moyenne = 12.5;\nString nom = "Amina";\nboolean reussi = moyenne >= 10;\n\nSystem.out.println(7 / 2);            // 3\nSystem.out.println(7 / 2.0);          // 3.5\nSystem.out.println(Integer.parseInt("12") + 1);  // 13	234	\N
16	11	6	cpp	Variables et types en C++ — Compilation et types	21	#include <string>\n\nint age = 20;\ndouble moyenne = 12.5;\nstd::string nom = "Amina";\nbool reussi = moyenne >= 10;\n\nstd::cout << 7 / 2 << std::endl;                       // 3\nstd::cout << static_cast<double>(7) / 2 << std::endl;  // 3.5	234	\N
17	11	6	cpp	Variables et types en C++ — Compilation et types	11	En C++, le type précède le nom de la variable et sa taille dépend de la plateforme. Les types usuels sont int, double, bool et std::string, qui exige #include <string>. La division entre deux entiers est entière : 7 / 2 vaut 3. Une conversion explicite s'écrit avec static_cast<double>, plus sûre qu'un transtypage à la C.	322	\N
18	1	7	python	Conditions en Python — Variables et types	21	note = 13\n\nif note >= 16:\n    mention = "très bien"\nelif note >= 12:\n    mention = "bien"\nelse:\n    mention = "à revoir"\n\nprint(mention)   # bien	145	\N
19	1	7	python	Conditions en Python — Variables et types	11	Une condition s'écrit avec if, elif et else, suivie de deux-points. Contrairement à Java et C++, les parenthèses ne sont pas nécessaires et le bloc est délimité par l'indentation : mélanger tabulations et espaces provoque une IndentationError. La comparaison d'égalité utilise deux signes égaux, l'affectation un seul.	318	\N
20	6	8	java	Conditions en Java — Classes et objets	11	En Java, la condition d'un if est une expression booléenne entre parenthèses : il n'existe pas de conversion automatique d'un entier vers un booléen, écrire if (note) ne compile pas. Les blocs sont délimités par des accolades ; sans accolades, seule l'instruction suivante appartient au if, ce qui est une source classique d'erreur.	332	\N
21	6	8	java	Conditions en Java — Classes et objets	21	int note = 13;\nString mention;\n\nif (note >= 16) {\n    mention = "très bien";\n} else if (note >= 12) {\n    mention = "bien";\n} else {\n    mention = "à revoir";\n}\n\nSystem.out.println(mention);   // bien	200	\N
22	11	9	cpp	Conditions en C++ — Compilation et types	21	int note = 13;\nstd::string mention;\n\nif (note >= 16) {\n    mention = "très bien";\n} else if (note >= 12) {\n    mention = "bien";\n} else {\n    mention = "à revoir";\n}\n\nstd::cout << mention << std::endl;   // bien	211	\N
23	11	9	cpp	Conditions en C++ — Compilation et types	11	En C++, la condition d'un if est convertie en booléen : un entier nul vaut faux, tout autre entier vaut vrai. Cette souplesse est un piège : la faute de frappe if (x = 5), qui affecte au lieu de comparer, compile sans avertissement et rend la condition toujours vraie. Les compilateurs modernes la signalent avec l'option -Wall.	328	\N
24	1	10	python	Boucles en Python — Variables et types	21	notes = [12, 15, 9]\nsomme = 0\nfor note in notes:\n    somme += note\nprint(somme / len(notes))   # 12.0\n\ni = 0\nwhile i < 3:\n    i += 1          # sans cette ligne : boucle infinie\nprint(i)            # 3	201	\N
25	1	10	python	Boucles en Python — Variables et types	11	La boucle for parcourt directement les éléments d'une séquence, sans compteur ni condition d'arrêt, comme dans for note in notes. Pour répéter un nombre connu de fois, on utilise range(n), qui produit les entiers de 0 à n-1. La boucle while répète tant qu'une condition reste vraie ; si cette condition ne devient jamais fausse, le programme ne se termine pas.	360	\N
26	6	11	java	Boucles en Java — Classes et objets	21	int[] notes = {12, 15, 9};\nint somme = 0;\n\nfor (int note : notes) {\n    somme += note;\n}\nSystem.out.println((double) somme / notes.length);   // 12.0\n\nfor (int i = 0; i < notes.length; i++) {\n    System.out.print(notes[i] + " ");\n}	231	\N
27	6	11	java	Boucles en Java — Classes et objets	11	Java propose trois formes : le for classique avec initialisation, condition et incrément ; le for-each pour parcourir un tableau sans indice ; et le while pour une condition inconnue à l'avance. Le parcours par indice impose de rester dans les bornes : un tableau de taille n se parcourt de 0 à n-1, sinon une ArrayIndexOutOfBoundsException est levée.	351	\N
28	11	12	cpp	Boucles en C++ — Compilation et types	21	#include <vector>\n\nstd::vector<int> notes{12, 15, 9};\nint somme = 0;\n\nfor (int n : notes) {\n    somme += n;\n}\nstd::cout << static_cast<double>(somme) / notes.size() << std::endl;  // 12\n\nfor (int& n : notes) {\n    n += 1;      // modifie le vecteur : passage par référence\n}	274	\N
29	11	12	cpp	Boucles en C++ — Compilation et types	11	C++ reprend les trois formes de Java : for classique, for sur intervalle et while. Le for sur intervalle s'écrit for (int n : notes) et parcourt une copie de chaque élément ; pour modifier le contenu, on écrit for (int& n : notes). Comme en Java, un indice hors bornes n'est pas vérifié à l'exécution : le comportement est indéfini.	332	\N
30	1	13	python	Fonctions et valeur de retour en Python — Variables et types	11	Une fonction se déclare avec le mot-clé def et son corps est indenté. Une fonction qui ne contient aucun return, ou dont le return est sans valeur, renvoie None : c'est la valeur de retour par défaut en Python. Pour renvoyer un résultat, écrivez return suivi de la valeur, et veillez à ce que tous les chemins d'exécution atteignent un return.	343	\N
31	1	13	python	Fonctions et valeur de retour en Python — Variables et types	21	def moyenne(notes):\n    """Retourne la moyenne d'une liste de notes."""\n    if not notes:\n        return 0\n    return sum(notes) / len(notes)\n\ndef sans_retour(notes):\n    total = sum(notes)          # aucun return\n\nprint(moyenne([12, 15, 9]))     # 12.0\nprint(sans_retour([12, 15, 9])) # None	292	\N
32	6	14	java	Méthodes et valeur de retour en Java — Classes et objets	21	public static double moyenne(int[] notes) {\n    if (notes.length == 0) {\n        return 0;\n    }\n    int somme = 0;\n    for (int n : notes) {\n        somme += n;\n    }\n    return (double) somme / notes.length;\n}\n\n// moyenne(new int[]{12, 15, 9}) vaut 12.0	255	\N
33	6	14	java	Méthodes et valeur de retour en Java — Classes et objets	11	Une méthode déclare son type de retour avant son nom. Si ce type est void, la méthode ne renvoie rien ; sinon, tous les chemins d'exécution doivent atteindre un return, faute de quoi le compilateur refuse la compilation. Une méthode peut aussi lever une exception plutôt que renvoyer une valeur en cas d'erreur.	311	\N
34	11	15	cpp	Fonctions, retour et surcharge en C++ — Compilation et types	11	En C++, le type de retour est déclaré avant le nom de la fonction. Toute fonction non void doit retourner une valeur sur chaque chemin, sinon le comportement est indéfini. C++ autorise la surcharge : deux fonctions peuvent porter le même nom si leurs paramètres diffèrent, ce qui n'est pas possible en Python. Enfin, un paramètre volumineux se passe par référence constante (const std::vector<int>&) pour éviter une copie à chaque appel.	437	\N
35	11	15	cpp	Fonctions, retour et surcharge en C++ — Compilation et types	21	#include <vector>\n\ndouble moyenne(const std::vector<int>& notes) {\n    if (notes.empty()) {\n        return 0.0;\n    }\n    int somme = 0;\n    for (int n : notes) {\n        somme += n;\n    }\n    return static_cast<double>(somme) / notes.size();\n}\n\n// surcharge : même nom, paramètres différents\nint moyenne(int a, int b) {\n    return (a + b) / 2;\n}	346	\N
36	1	16	python	Listes et dictionnaires en Python — Variables et types	11	La liste est la collection ordonnée et modifiable de Python : elle s'écrit entre crochets et accepte des éléments de types différents. Sa taille s'obtient avec len(liste) et l'accès par indice commence à 0. Le dictionnaire associe des clés à des valeurs avec la syntaxe {clé: valeur} ; l'accès à une clé absente avec crochets lève une KeyError, alors que get renvoie None.	372	\N
37	1	16	python	Listes et dictionnaires en Python — Variables et types	21	notes = [12, 15, 9]\nnotes.append(11)          # ajout en fin\nprint(len(notes))         # 4\nprint(notes[0])           # 12\n\nbulletin = {"Amina": 16, "Karim": 9}\nprint(bulletin.get("Lina"))   # None, pas de KeyError\nfor nom, note in bulletin.items():\n    print(nom, note)	269	\N
38	6	17	java	Collections en Java — Classes et objets	21	import java.util.*;\n\nList<Integer> notes = new ArrayList<>(List.of(12, 15, 9));\nnotes.add(11);\nSystem.out.println(notes.size());      // 4\nSystem.out.println(notes.get(0));      // 12\n\nMap<String, Integer> bulletin = new HashMap<>();\nbulletin.put("Amina", 16);\nSystem.out.println(bulletin.getOrDefault("Lina", 0));   // 0\n\nint[] tableau = {12, 15, 9};\nSystem.out.println(tableau.length);    // 3, sans parenthèses	413	\N
39	6	17	java	Collections en Java — Classes et objets	11	En Java, la taille d'un tableau est fixée à sa création et s'obtient avec length, sans parenthèses. Pour une taille variable, on utilise les collections : ArrayList pour une liste modifiable, HashMap pour un dictionnaire. Leur taille s'obtient avec size(), avec des parenthèses. Confondre length et size() est l'erreur de compilation la plus fréquente.	352	\N
40	11	18	cpp	Conteneurs STL en C++ — Compilation et types	21	#include <vector>\n#include <map>\n\nstd::vector<int> notes{12, 15, 9};\nnotes.push_back(11);\nstd::cout << notes.size() << std::endl;   // 4\nstd::cout << notes.at(0) << std::endl;    // 12, avec vérification\n\nstd::map<std::string, int> bulletin;\nbulletin["Amina"] = 16;\nstd::cout << bulletin.size() << std::endl;  // 1	314	\N
41	11	18	cpp	Conteneurs STL en C++ — Compilation et types	11	La bibliothèque standard de C++ fournit std::vector pour un tableau redimensionnable et std::map pour un dictionnaire ordonné. Leur taille s'obtient avec size(). L'accès par crochets ne vérifie pas les bornes : utiliser at() lève une exception si l'indice est hors limites. Un std::vector se passe par référence constante pour éviter une copie coûteuse.	353	\N
42	1	19	python	Erreurs et exceptions en Python — Variables et types	21	notes = []\n\ntry:\n    moyenne = sum(notes) / len(notes)\nexcept ZeroDivisionError:\n    moyenne = 0     # on traite le cas, on ne masque pas l'erreur\n\nprint(moyenne)      # 0\n\n# IndexError : notes[0] lèverait une exception	219	\N
43	1	19	python	Erreurs et exceptions en Python — Variables et types	11	Python distingue les erreurs de syntaxe, détectées avant l'exécution, des exceptions, déclenchées pendant l'exécution. Une exception se capture avec try et except ; capturer une exception trop large masque les fautes de programmation. La division par zéro lève ZeroDivisionError, un indice hors bornes lève IndexError.	318	\N
44	6	20	java	Exceptions en Java — Classes et objets	11	En Java, une exception se capture avec try et catch, et le bloc finally s'exécute dans tous les cas. Les exceptions vérifiées, comme IOException, doivent être déclarées ou capturées : le compilateur l'impose. Les exceptions non vérifiées, comme NullPointerException ou ArrayIndexOutOfBoundsException, traduisent une faute de programmation et ne devraient pas être capturées à la légère.	386	\N
45	6	20	java	Exceptions en Java — Classes et objets	21	int[] notes = new int[0];\n\ntry {\n    System.out.println(notes[0]);\n} catch (ArrayIndexOutOfBoundsException e) {\n    System.out.println("indice hors bornes : " + e.getMessage());\n} finally {\n    System.out.println("fin du traitement");\n}	236	\N
46	11	21	cpp	Exceptions en C++ — Compilation et types	11	C++ utilise try et catch, mais rien n'oblige à traiter une exception : une exception non capturée termine le programme. Le conteneur std::vector lève std::out_of_range si l'on utilise at() avec un indice invalide, alors que l'opérateur crochets ne vérifie rien. Le RAII garantit que les ressources sont libérées même en cas d'exception.	336	\N
47	11	21	cpp	Exceptions en C++ — Compilation et types	21	#include <stdexcept>\n#include <vector>\n\nstd::vector<int> notes;\n\ntry {\n    std::cout << notes.at(0) << std::endl;\n} catch (const std::out_of_range& e) {\n    std::cout << "indice hors bornes : " << e.what() << std::endl;\n}	221	\N
\.


--
-- Data for Name: invitations_inscription; Type: TABLE DATA; Schema: public; Owner: ihm2026
--

COPY public.invitations_inscription (id, email_cible, role_cible, empreinte_pin, expire_le, echecs, utilise_le, cree_par, cree_le) FROM stdin;
1ac62bc9-5eb8-4257-af4c-51466b328084	samoumegharba76@gmail.com	APPRENANT	$2a$12$icGrv19tpTIkWRPX1lUfneU4HvAi2rekYM2cRTudUNcEhfBHE.O.m	2026-11-03 15:30:37.670354+01	0	2026-11-01 15:32:04.604501+01	1	2026-11-01 15:30:37.670354+01
821a2f97-d0c4-479e-be05-62cd62c5b80f	samoumegharba03@gmail.com	ENSEIGNANT	$2a$12$5gnX0QFkXVovCpG3YyMEeuop8VBXxsmmfiXAnZUAPL58VT7TOQVDu	2026-11-03 15:36:30.923297+01	0	2026-11-01 15:37:05.334416+01	1	2026-11-01 15:36:30.923297+01
e956af83-8086-45d1-82bd-639ff579ce01	marzoukamine47@gmail.com	ENSEIGNANT	$2a$12$4vGHOzZJFPi8jzxtLqOhCuax4Mlr8p5gO6zBXCYaeRpU3MZCvw6EW	2026-11-04 11:54:58.619297+01	0	\N	1	2026-11-02 11:54:58.619297+01
4a0b205d-6405-48af-ad20-7d4b523665cc	merzoukamine47@gmail.com	ENSEIGNANT	$2a$12$q6qMh1l.kZw1Ekgm4SGcNuuN1V4q5YxYSvnCm.BKcfQpa7EK4nIHS	2026-11-04 11:56:38.02194+01	0	2026-11-02 11:57:16.522511+01	1	2026-11-02 11:56:38.02194+01
41cc6647-7692-4f8c-b552-0e4a04545ae2	aymenserdani982@gmail.com	ENSEIGNANT	$2a$12$4ZM7xH4qNMutiojnTOokX.T9y3YhoqnsfHVtI29cUnSxOGPZIOdne	2026-11-04 16:53:00.319547+01	0	2026-11-02 16:53:51.704036+01	1	2026-11-02 16:53:00.319547+01
1f98d660-6395-4319-b8e2-821be0bc3a0d	amnezrouk07@gmail.com	APPRENANT	$2a$12$tscO8jPFp2QjmrpgLaPFHeNgz98Tb5j14.G9REvOpAYFbu64wHTkq	2026-11-04 18:13:41.2482+01	0	2026-11-02 18:15:32.003747+01	\N	2026-11-02 18:13:41.2482+01
\.


--
-- Data for Name: journal_audit; Type: TABLE DATA; Schema: public; Owner: ihm2026
--

COPY public.journal_audit (id, acteur_id, action, cible, details, cree_le) FROM stdin;
1	2	PUBLICATION_NIVEAU	niveaux/3	{"seuil": 12, "parcours": "python", "tentatives": 3}	2026-10-28 21:21:12.527846+01
2	\N	INSCRIPTION_COMPTE	samoumegharba76@gmail.com	{"role": "APPRENANT", "actif": false}	2026-11-01 15:30:18.646482+01
3	1	ACCEPTATION_INSCRIPTION	samoumegharba76@gmail.com	{"role": "APPRENANT", "actif": false}	2026-11-01 15:30:37.670354+01
4	1	CREATION_PIN_ACTIVATION	samoumegharba76@gmail.com	{"role": "APPRENANT", "actif": false}	2026-11-01 15:30:37.670354+01
5	\N	ACTIVATION_PAR_PIN	samoumegharba76@gmail.com	{"role": "APPRENANT", "actif": true}	2026-11-01 15:32:04.604501+01
6	\N	INSCRIPTION_COMPTE	samoumegharba03@gmail.com	{"role": "ENSEIGNANT", "actif": false}	2026-11-01 15:35:19.350959+01
7	1	ACCEPTATION_INSCRIPTION	samoumegharba03@gmail.com	{"role": "ENSEIGNANT", "actif": false}	2026-11-01 15:36:30.923297+01
8	1	CREATION_PIN_ACTIVATION	samoumegharba03@gmail.com	{"role": "ENSEIGNANT", "actif": false}	2026-11-01 15:36:30.923297+01
9	\N	ACTIVATION_PAR_PIN	samoumegharba03@gmail.com	{"role": "ENSEIGNANT", "actif": true}	2026-11-01 15:37:05.334416+01
10	1	DESACTIVATION_COMPTE	samoumegharba76@gmail.com	{"role": "APPRENANT", "actif": false}	2026-11-01 16:06:40.873697+01
11	1	ACTIVATION_COMPTE	samoumegharba76@gmail.com	{"role": "APPRENANT", "actif": true}	2026-11-01 16:07:32.191211+01
12	1	ACTIVATION_COMPTE	yacine.rami@univ.dz	{"role": "APPRENANT", "actif": true}	2026-11-01 16:07:40.543491+01
13	1	DESACTIVATION_COMPTE	yacine.rami@univ.dz	{"role": "APPRENANT", "actif": false}	2026-11-01 16:07:43.102073+01
14	1	AFFECTATION_APPRENANT	72	\N	2026-11-01 19:45:15.384042+01
15	73	CREATION_NIVEAU	16	\N	2026-11-01 20:42:28.014709+01
16	73	PUBLICATION_COURS_ENTRAINEMENT	16	\N	2026-11-01 20:44:10.411433+01
17	73	PREPARATION_TEST	2	\N	2026-11-01 20:44:45.75203+01
18	73	PUBLICATION_NIVEAU	16	\N	2026-11-01 21:16:12.895483+01
19	72	DEBUT_TEST	1	\N	2026-11-01 21:16:32.28822+01
20	72	SOUMISSION_TEST	1	\N	2026-11-01 21:19:49.377933+01
21	73	BROUILLON_CORRECTION	1	\N	2026-11-01 21:21:33.052161+01
22	73	PUBLICATION_CORRECTION	1	\N	2026-11-01 21:21:52.665993+01
23	72	DEBUT_TEST	2	\N	2026-11-01 21:22:17.852257+01
24	72	SOUMISSION_TEST	2	\N	2026-11-01 21:49:39.345686+01
25	73	PUBLICATION_CORRECTION	2	\N	2026-11-01 21:51:02.810969+01
26	\N	INSCRIPTION_COMPTE	marzoukamine47@gmail.com	{"role": "ENSEIGNANT", "actif": false}	2026-11-02 11:54:30.338088+01
27	1	ACCEPTATION_INSCRIPTION	marzoukamine47@gmail.com	{"role": "ENSEIGNANT", "actif": false}	2026-11-02 11:54:58.619297+01
28	1	CREATION_PIN_ACTIVATION	marzoukamine47@gmail.com	{"role": "ENSEIGNANT", "actif": false}	2026-11-02 11:54:58.619297+01
29	\N	INSCRIPTION_COMPTE	merzoukamine47@gmail.com	{"role": "ENSEIGNANT", "actif": false}	2026-11-02 11:56:31.748516+01
30	1	ACCEPTATION_INSCRIPTION	merzoukamine47@gmail.com	{"role": "ENSEIGNANT", "actif": false}	2026-11-02 11:56:38.02194+01
31	1	CREATION_PIN_ACTIVATION	merzoukamine47@gmail.com	{"role": "ENSEIGNANT", "actif": false}	2026-11-02 11:56:38.02194+01
32	\N	ACTIVATION_PAR_PIN	merzoukamine47@gmail.com	{"role": "ENSEIGNANT", "actif": true}	2026-11-02 11:57:16.522511+01
33	72	DEBUT_TEST	3	\N	2026-11-02 12:02:43.686219+01
34	1	AFFECTATION_APPRENANT	3	\N	2026-11-02 12:15:10.447207+01
35	76	CREATION_NIVEAU	17	\N	2026-11-02 12:18:34.125533+01
36	76	PUBLICATION_COURS_ENTRAINEMENT	17	\N	2026-11-02 12:18:58.639201+01
37	76	PREPARATION_TEST	3	\N	2026-11-02 12:19:03.912989+01
38	76	PUBLICATION_NIVEAU	17	\N	2026-11-02 12:19:08.588863+01
39	73	CREATION_NIVEAU	18	\N	2026-11-02 16:24:42.340418+01
40	73	PUBLICATION_COURS_ENTRAINEMENT	18	\N	2026-11-02 16:25:05.652408+01
41	73	PREPARATION_TEST	4	\N	2026-11-02 16:25:20.631492+01
42	73	PUBLICATION_NIVEAU	18	\N	2026-11-02 16:25:26.972633+01
43	72	SOUMISSION_TEST	3	\N	2026-11-02 16:27:05.94757+01
44	73	PUBLICATION_CORRECTION	3	\N	2026-11-02 16:28:03.239043+01
45	\N	INSCRIPTION_COMPTE	aymenserdani982@gmail.com	{"role": "ENSEIGNANT", "actif": false}	2026-11-02 16:51:09.650699+01
46	1	ACCEPTATION_INSCRIPTION	aymenserdani982@gmail.com	{"role": "ENSEIGNANT", "actif": false}	2026-11-02 16:53:00.319547+01
47	1	CREATION_PIN_ACTIVATION	aymenserdani982@gmail.com	{"role": "ENSEIGNANT", "actif": false}	2026-11-02 16:53:00.319547+01
48	\N	ACTIVATION_PAR_PIN	aymenserdani982@gmail.com	{"role": "ENSEIGNANT", "actif": true}	2026-11-02 16:53:51.704036+01
49	\N	INSCRIPTION_COMPTE	amnezrouk07@gmail.com	{"role": "APPRENANT", "actif": false}	2026-11-02 18:13:41.055494+01
50	\N	ACCEPTATION_BOT_INSCRIPTION	amnezrouk07@gmail.com	{"role": "APPRENANT", "actif": false}	2026-11-02 18:13:41.2482+01
51	\N	CREATION_PIN_ACTIVATION	amnezrouk07@gmail.com	{"role": "APPRENANT", "actif": false}	2026-11-02 18:13:41.2482+01
52	\N	ACTIVATION_PAR_PIN	amnezrouk07@gmail.com	{"role": "APPRENANT", "actif": true}	2026-11-02 18:15:32.003747+01
\.


--
-- Data for Name: langages; Type: TABLE DATA; Schema: public; Owner: ihm2026
--

COPY public.langages (id, code, libelle, version, image_execution) FROM stdin;
1	python	Python	3.12	python:3.12-slim
2	java	Java	OpenJDK 21 LTS	eclipse-temurin:21-jre
3	cpp	C++	C++20 (g++ 13)	gcc:13
\.


--
-- Data for Name: niveaux; Type: TABLE DATA; Schema: public; Owner: ihm2026
--

COPY public.niveaux (id, parcours_id, rang, titre, objectifs, seuil, tentatives_max, periode_debut, periode_fin, statut, cree_par, cree_le, maj_le) FROM stdin;
1	1	1	Variables et types	Déclarer, typer, convertir	10	3	\N	\N	PUBLIE	2	2026-10-28 21:21:12.527846+01	2026-10-28 21:21:12.527846+01
2	1	2	Conditions et boucles	if/elif/else, for, while	10	3	\N	\N	PUBLIE	2	2026-10-28 21:21:12.527846+01	2026-10-28 21:21:12.527846+01
3	1	3	Fonctions et modules	def, paramètres, import	12	3	\N	\N	PUBLIE	2	2026-10-28 21:21:12.527846+01	2026-10-28 21:21:12.527846+01
4	1	4	Structures de données	listes, dictionnaires, ensembles	12	3	\N	\N	PUBLIE	2	2026-10-28 21:21:12.527846+01	2026-10-28 21:21:12.527846+01
5	1	5	Fichiers et exceptions	open, with, try/except	12	2	\N	\N	PUBLIE	2	2026-10-28 21:21:12.527846+01	2026-10-28 21:21:12.527846+01
6	2	1	Classes et objets	attributs, méthodes, constructeurs	12	3	\N	\N	PUBLIE	2	2026-10-28 21:21:12.527846+01	2026-10-28 21:21:12.527846+01
7	2	2	Héritage et interfaces	extends, implements, polymorphisme	12	3	\N	\N	PUBLIE	2	2026-10-28 21:21:12.527846+01	2026-10-28 21:21:12.527846+01
8	2	3	Collections et généricité	List, Map, <T>	12	3	\N	\N	PUBLIE	2	2026-10-28 21:21:12.527846+01	2026-10-28 21:21:12.527846+01
9	2	4	Exceptions et flux	try/catch, I/O	12	2	\N	\N	PUBLIE	2	2026-10-28 21:21:12.527846+01	2026-10-28 21:21:12.527846+01
10	2	5	Interface graphique et MVC	JavaFX, FXML, contrôleurs	12	2	\N	\N	PUBLIE	2	2026-10-28 21:21:12.527846+01	2026-10-28 21:21:12.527846+01
11	3	1	Compilation et types	préprocesseur, types, compilation	10	3	\N	\N	PUBLIE	2	2026-10-28 21:21:12.527846+01	2026-10-28 21:21:12.527846+01
12	3	2	Pointeurs et références	adressage, passage par référence	10	3	\N	\N	PUBLIE	2	2026-10-28 21:21:12.527846+01	2026-10-28 21:21:12.527846+01
13	3	3	Classes et surcharge	constructeurs, opérateurs	10	3	\N	\N	PUBLIE	2	2026-10-28 21:21:12.527846+01	2026-10-28 21:21:12.527846+01
14	3	4	STL et templates	vector, map, algorithm	10	2	\N	\N	PUBLIE	2	2026-10-28 21:21:12.527846+01	2026-10-28 21:21:12.527846+01
15	3	5	Gestion mémoire	new/delete, RAII, smart pointers	10	2	\N	\N	PUBLIE	2	2026-10-28 21:21:12.527846+01	2026-10-28 21:21:12.527846+01
16	4	1	Variable et Types	\N	20	3	\N	\N	PUBLIE	73	2026-11-01 20:42:28.014709+01	2026-11-01 21:16:12.895483+01
17	5	1	Conditions et comparaisons	\N	12	3	\N	\N	PUBLIE	76	2026-11-02 12:18:34.125533+01	2026-11-02 12:19:08.588863+01
18	4	2	Conditions et comparaisons	\N	20	3	\N	\N	PUBLIE	73	2026-11-02 16:24:42.340418+01	2026-11-02 16:25:26.972633+01
\.


--
-- Data for Name: parcours; Type: TABLE DATA; Schema: public; Owner: ihm2026
--

COPY public.parcours (id, langage_id, libelle, actif) FROM stdin;
1	1	Parcours Python	t
2	2	Parcours Java	t
3	3	Parcours C++	t
4	1	Parcours enseignant 73 — python	t
5	1	Parcours enseignant 76 — python	t
\.


--
-- Data for Name: parcours_enseignants; Type: TABLE DATA; Schema: public; Owner: ihm2026
--

COPY public.parcours_enseignants (enseignant_id, langage_id, parcours_id) FROM stdin;
73	1	4
76	1	5
\.


--
-- Data for Name: progressions; Type: TABLE DATA; Schema: public; Owner: ihm2026
--

COPY public.progressions (id, apprenant_id, niveau_id, etat, meilleure_note, valide_le, maj_le) FROM stdin;
1	3	1	VALIDE	16.00	2026-10-08 21:21:12.527846+01	2026-10-28 21:21:12.527846+01
2	3	2	VALIDE	13.00	2026-10-08 21:21:12.527846+01	2026-10-28 21:21:12.527846+01
3	3	3	EN_COURS	\N	\N	2026-10-28 21:21:12.527846+01
4	4	3	EN_COURS	9.00	\N	2026-10-28 21:21:12.527846+01
5	5	3	EN_COURS	11.00	\N	2026-10-28 21:21:12.527846+01
6	72	16	VALIDE	20.00	2026-11-01 21:21:52.665993+01	2026-11-02 16:28:03.239043+01
\.


--
-- Data for Name: propositions; Type: TABLE DATA; Schema: public; Owner: ihm2026
--

COPY public.propositions (id, question_id, "position", texte, correcte) FROM stdin;
1	1	4	void	f
2	1	3	function	f
3	1	2	def	t
4	1	1	func	f
5	2	4	Erreur	f
6	2	3	4	f
7	2	2	3	t
8	2	1	3.5	f
9	3	4	Le fichier	f
10	3	3	Le bloc où elle est déclarée	t
11	3	2	La classe	f
12	3	1	Tout le programme	f
\.


--
-- Data for Name: questions; Type: TABLE DATA; Schema: public; Owner: ihm2026
--

COPY public.questions (id, test_id, "position", type, enonce, bareme, active_pedagogique) FROM stdin;
1	1	1	QCM	Quel mot-clé déclare une fonction en Python ?	1.00	t
2	1	2	QCM	Que vaut 7 // 2 en Python ?	1.00	t
3	1	3	QCM	Quelle est la portée d'une variable déclarée dans un bloc en Java ?	1.00	t
4	2	1	REPONSE_COURTE	Explique avec tes propres mots ce qu’est une variable en Python et donne un exemple d’affectation.	1.00	t
5	2	2	REPONSE_COURTE	Indique le type de chacune de ces valeurs : "Bonjour", 18, 12.5 et False.	1.00	t
6	2	3	REPONSE_COURTE	Écris quatre affectations : prenom contenant "Karim", age contenant 19, moyenne contenant 13.5 et est_inscrit contenant True.	1.00	t
7	2	4	REPONSE_COURTE	Quelle différence existe-t-il entre age = 19 et age = "19" ? Précise le type de chaque valeur.	1.00	t
8	2	5	REPONSE_COURTE	On exécute successivement age = 19, puis age = age + 1, puis print(age). Quelle valeur est affichée ? Explique pourquoi.	1.00	t
9	2	6	REPONSE_COURTE	Le nom de variable 2notes est-il valide en Python ? Justifie et propose un nom valide.	1.00	t
10	2	7	REPONSE_COURTE	Écris une instruction qui affiche le texte "Moyenne :" suivi de la valeur de la variable moyenne.	1.00	t
11	2	8	REPONSE_COURTE	Écris un petit programme qui crée une variable ville contenant une chaîne de caractères et une variable temperature contenant un nombre décimal, puis affiche leurs valeurs et leurs types.	1.00	t
12	3	1	REPONSE_COURTE	Explique la différence entre = et == en Python et donne un exemple de chacun.	1.00	t
13	3	2	REPONSE_COURTE	Indique le résultat de chaque comparaison : 8 > 3, 8 == 3, 10 >= 10 et 5 != 5.	1.00	t
14	3	3	REPONSE_COURTE	Écris un programme utilisant age = 17 qui affiche "Majeur" si l'âge est supérieur ou égal à 18, sinon "Mineur".	1.00	t
15	3	4	REPONSE_COURTE	Explique pourquoi les deux-points et l'indentation sont importants dans une instruction if.	1.00	t
16	3	5	REPONSE_COURTE	Avec temperature = 28, le programme teste if temperature < 10, puis elif temperature < 25, puis else. Quel bloc est exécuté ? Justifie.	1.00	t
17	3	6	REPONSE_COURTE	Écris une condition qui vérifie qu'une variable note est comprise entre 0 et 20, bornes incluses.	1.00	t
18	3	7	REPONSE_COURTE	Explique la différence entre and et or et donne un exemple de condition pour chacun.	1.00	t
19	3	8	REPONSE_COURTE	Écris un programme utilisant nombre = -3 qui affiche "Positif", "Négatif" ou "Zéro" selon la valeur de nombre.	1.00	t
20	4	1	REPONSE_COURTE	Explique la différence entre = et == en Python et donne un exemple de chacun.	1.00	t
21	4	2	REPONSE_COURTE	Indique le résultat de chaque comparaison : 8 > 3, 8 == 3, 10 >= 10 et 5 != 5.	1.00	t
22	4	3	REPONSE_COURTE	Écris un programme utilisant age = 17 qui affiche "Majeur" si l'âge est supérieur ou égal à 18, sinon "Mineur".	1.00	t
23	4	4	REPONSE_COURTE	Explique pourquoi les deux-points et l'indentation sont importants dans une instruction if.	1.00	t
24	4	5	REPONSE_COURTE	Avec temperature = 28, le programme teste if temperature < 10, puis elif temperature < 25, puis else. Quel bloc est exécuté ? Justifie.	1.00	t
25	4	6	REPONSE_COURTE	Écris une condition qui vérifie qu'une variable note est comprise entre 0 et 20, bornes incluses.	1.00	t
26	4	7	REPONSE_COURTE	Explique la différence entre and et or et donne un exemple de condition pour chacun.	1.00	t
27	4	8	REPONSE_COURTE	Écris un programme utilisant nombre = -3 qui affiche "Positif", "Négatif" ou "Zéro" selon la valeur de nombre.	1.00	t
\.


--
-- Data for Name: reponses; Type: TABLE DATA; Schema: public; Owner: ihm2026
--

COPY public.reponses (id, tentative_id, question_id, proposition_id, texte_libre, correcte) FROM stdin;
\.


--
-- Data for Name: ressources; Type: TABLE DATA; Schema: public; Owner: ihm2026
--

COPY public.ressources (id, niveau_id, libelle, url) FROM stdin;
\.


--
-- Data for Name: sessions; Type: TABLE DATA; Schema: public; Owner: ihm2026
--

COPY public.sessions (id, utilisateur_id, empreinte_jeton, expire_le, cree_le, derniere_activite, expiration_max) FROM stdin;
b19385d0-b5a6-4302-bbe6-cfd15b9a8b12	72	cad1e53bf8fc4eb08204b5737863047dd89359f1da2f2ca6b65f2236ab0fd250	2026-11-02 17:14:15.974451+01	2026-11-02 16:38:25.956137+01	2026-11-02 16:44:15.974451+01	2026-11-03 00:38:25.956137+01
d25d9fd0-2322-4c3e-be98-63b5ee47303c	73	b6fcf63abe8567a3ee292f51110117296890f0e8f248a8586c7e131723c1ac83	2026-11-01 21:57:13.012423+01	2026-11-01 21:14:28.518204+01	2026-11-01 21:27:13.012423+01	2026-11-02 05:14:28.518204+01
e8a77913-a5b6-4de7-a619-716eb49e99f0	1	5fe91625c1a39e9b0200e3bfa14d0c8d67cef5288755347238f92dfbe89107e9	2026-11-01 16:37:43.057992+01	2026-11-01 16:02:14.637363+01	2026-11-01 16:07:43.057992+01	2026-11-02 00:02:14.637363+01
0ce21165-e0a3-4ad6-b022-6d9239581684	72	c2ba18b9408939f5a67ccaab58c99bbbed5e61027661ede08d37ee89d158fe20	2026-11-01 16:38:18.70025+01	2026-11-01 16:08:18.70025+01	2026-11-01 16:08:18.70025+01	2026-11-02 00:08:18.70025+01
e26ed792-3cd3-4f78-b7fc-f1f09bfebf69	1	f805d414fc5ceb3d78b1fb1e5782be86acdd8015f2b0bcf27fc47ae7d4e0df86	2026-11-01 17:01:44.380341+01	2026-11-01 16:31:44.026699+01	2026-11-01 16:31:44.380341+01	2026-11-02 00:31:44.026699+01
eda61927-7cf3-4f8a-b58b-3379dd3f6f9d	72	947a06fa607bb8552173a2a198f79a5e8e3a9a92596f227621decc07ff35efcb	2026-11-01 20:16:28.784879+01	2026-11-01 19:40:33.494752+01	2026-11-01 19:46:28.784879+01	2026-11-02 03:40:33.494752+01
91e74627-2491-4e08-b898-2ab46115d5fe	73	230579d863fe6d5e522c32c78ecd537210658fa27acea450bdb01aa59100f4a7	2026-11-01 17:16:15.663232+01	2026-11-01 16:46:15.663232+01	2026-11-01 16:46:15.663232+01	2026-11-02 00:46:15.663232+01
8ce074e1-4a28-47e9-bfe9-861b3bd742f7	1	a551fcfb26c672091be509a83b737909277ad5202394ded7435b5449486ac190	2026-11-01 17:19:19.865304+01	2026-11-01 16:44:02.237526+01	2026-11-01 16:49:19.865304+01	2026-11-02 00:44:02.237526+01
f9596340-946c-4bb8-820e-1d3a1b9f9d58	72	04d49ef9e56a74212b880ec982db94c3b8bf5302b88a7c2fdf88fb0f17663588	2026-11-01 17:20:08.047522+01	2026-11-01 16:50:08.047522+01	2026-11-01 16:50:08.047522+01	2026-11-02 00:50:08.047522+01
4a19fcd8-0017-4cd7-b940-3e75458f1532	1	a059d60564e6970f728014d025264b2867141ab1b0b8ca9a002413b7a0e4cddc	2026-11-01 15:30:45.32288+01	2026-11-01 15:00:27.470938+01	2026-11-01 15:00:45.32288+01	2026-11-01 23:00:27.470938+01
b46c0194-caf7-478c-8118-bf916e7c9e90	1	b947c912e4ed5a232ebf1907fb74b5831aaf78be4e02f451c97b5de9627aed13	2026-11-01 15:33:48.592408+01	2026-11-01 15:03:48.295056+01	2026-11-01 15:03:48.592408+01	2026-11-01 23:03:48.295056+01
0ac2a9e8-08f0-455c-83fa-7878b1f3c2c3	1	1e4e74642ea5fa8aa8f8b0ef6e1d2f53517324e2f5d581d100b896b07c58d543	2026-11-01 18:28:27.185901+01	2026-11-01 17:58:26.888393+01	2026-11-01 17:58:27.185901+01	2026-11-02 01:58:26.888393+01
983c768b-5267-4c3d-929c-91d5248c3dd9	73	12c6df7c08ac65de6401bbc8ee254af399077f5a7aec37093fdff0611541f5da	2026-11-01 18:31:09.545793+01	2026-11-01 18:01:09.545793+01	2026-11-01 18:01:09.545793+01	2026-11-02 02:01:09.545793+01
2cdae9aa-2fd5-4cc6-b2b1-a09b0e2f6515	1	fe57f9b176132b97b8419262c692180b1929b56247979741d8ad1958af9fe06f	2026-11-01 16:00:37.640222+01	2026-11-01 15:28:58.130905+01	2026-11-01 15:30:37.640222+01	2026-11-01 23:28:58.130905+01
131e3b68-6488-4d8d-ae0f-46ba7a22b89e	72	84a07037d2a0592b261b60926c74f2cf246f4f35988696ac4cb2e3299b81597c	2026-11-01 16:02:40.518345+01	2026-11-01 15:32:40.518345+01	2026-11-01 15:32:40.518345+01	2026-11-01 23:32:40.518345+01
b847a5ca-c3e0-455b-957f-2221739105f2	72	69224ef67524d7e2301b0c72f930c1d5f6d38af549aef45011e06c8ea42ecf0d	2026-11-01 18:32:59.014664+01	2026-11-01 18:02:38.431365+01	2026-11-01 18:02:59.014664+01	2026-11-02 02:02:38.431365+01
7c0e6ad6-4163-450d-ac7b-1d46ce46e1eb	1	1e172ea1e48ef9c121533c2581013bcaeaab62dc3190bc73686d96b847d07f8f	2026-11-01 16:06:30.888829+01	2026-11-01 15:36:24.724165+01	2026-11-01 15:36:30.888829+01	2026-11-01 23:36:24.724165+01
fccbad3a-80db-49f0-8f2f-1be1c75a3faa	73	f6c5533961b520ee284cb0375a1b405c641ffd62de1fc66ea044c881275a2fd0	2026-11-02 12:20:45.400151+01	2026-11-02 11:50:06.962441+01	2026-11-02 11:50:45.400151+01	2026-11-02 19:50:06.962441+01
a2458420-a508-4618-b689-3c08ac769dd7	73	488404d0c9b40318d344bec7aa8bbe03a2f71bb2644bffb68d33473afaac3276	2026-11-01 16:34:59.404007+01	2026-11-01 16:04:59.404007+01	2026-11-01 16:04:59.404007+01	2026-11-02 00:04:59.404007+01
6f6a0962-2f79-49b2-afa0-9e190e0a4db0	72	8a9d64632cb849ea1c25358729b0abc214327c78de32adda5b0ead0e045a468b	2026-11-01 21:57:17.758708+01	2026-11-01 21:13:14.487651+01	2026-11-01 21:27:17.758708+01	2026-11-02 05:13:14.487651+01
93bcdd41-920e-429d-b692-db4b4d1d1ce2	72	bf9700336a81d651b0e46b1fbc603724ff5fbf777e46bb6a75a461dc5c1b2c0b	2026-11-01 20:29:51.756975+01	2026-11-01 19:58:10.931439+01	2026-11-01 19:59:51.756975+01	2026-11-02 03:58:10.931439+01
8d1e1a7d-0890-4d3d-84eb-3ab9f0265007	72	0007f56304f58c3a4203ae020fe3b9bc4e03080d57e479445cc1df2d587f714d	2026-11-01 18:58:53.023968+01	2026-11-01 18:24:00.299083+01	2026-11-01 18:28:53.023968+01	2026-11-02 02:24:00.299083+01
e4727910-95fb-4327-9410-07fd8e571510	73	5c6307e9545338f10c1733034a731303ca0224e4d2ac35403a8bea134f79acb3	2026-11-01 19:50:17.519803+01	2026-11-01 19:20:17.519803+01	2026-11-01 19:20:17.519803+01	2026-11-02 03:20:17.519803+01
5576d1f1-20b4-436a-9d57-583bc16757f5	72	e5e128c93693da818608918c58f7d4ee449c4c619ebf1f5455ab7d97d7e872a6	2026-11-01 23:14:04.057356+01	2026-11-01 22:27:40.712304+01	2026-11-01 22:44:04.057356+01	2026-11-02 06:27:40.712304+01
b5bc3825-1115-4825-8e91-6308584d31ef	72	74c6b79c1277398e9e0cb703e93dcef5685026ef111ace43ac3f4c4f876bea89	2026-11-01 20:06:48.662159+01	2026-11-01 19:36:48.662159+01	2026-11-01 19:36:48.662159+01	2026-11-02 03:36:48.662159+01
10ff3b69-9207-4dff-8e32-ba4549ce68e6	73	5644dbb310a9060bb45a39069436f83fae79129bab92d2ec129141431b7267d4	2026-11-01 20:09:06.034203+01	2026-11-01 19:39:06.034203+01	2026-11-01 19:39:06.034203+01	2026-11-02 03:39:06.034203+01
baa82a94-e3ba-425e-a864-3ff8cc9f3eea	1	c1a4276b9aead03e08f805b38aa1ee752ba6209fbaa3306bd97da2009e5e2fdb	2026-11-01 20:37:00.062434+01	2026-11-01 20:06:52.924089+01	2026-11-01 20:07:00.062434+01	2026-11-02 04:06:52.924089+01
be357819-07e3-4557-9bef-392ac42e7d91	1	9900665bf94591148fa03da303ec92af1d3e9ea4fb1716789e6f75d920147b15	2026-11-01 20:15:15.345995+01	2026-11-01 19:44:45.311439+01	2026-11-01 19:45:15.345995+01	2026-11-02 03:44:45.311439+01
8b1527b0-52a7-4a16-a1ef-ab42ba8d8a4b	72	0e21c1e2ce330c691d56ed2a4cdfffefcb2563d3ad37d534918d121c1ce3c3f4	2026-11-01 21:25:52.447901+01	2026-11-01 20:46:19.440137+01	2026-11-01 20:55:52.447901+01	2026-11-02 04:46:19.440137+01
464bfa5b-c534-4866-8e6e-fa77f49357d9	72	475135b602395361767daf8f57117d11f4a6e85cee31b6fc7f09e7758de0cc78	2026-11-01 20:17:57.190722+01	2026-11-01 19:47:24.795748+01	2026-11-01 19:47:57.190722+01	2026-11-02 03:47:24.795748+01
a94235f8-2ac8-4f31-aa91-e86f8285a95f	73	0e690bb5358b7e0de282717ffa441be6f6e191744805c3e475fc36a49eb11a91	2026-11-01 20:15:40.648711+01	2026-11-01 19:42:09.455383+01	2026-11-01 19:45:40.648711+01	2026-11-02 03:42:09.455383+01
4f9996c2-18bc-4e71-a8a8-ca69916c0ce8	73	f0725979ac34dee84ed4f4ca1389e15cddfb8cd6c7ddc65dba83a47967017231	2026-11-01 22:21:02.940368+01	2026-11-01 21:50:40.997489+01	2026-11-01 21:51:02.940368+01	2026-11-02 05:50:40.997489+01
329f75a8-3217-4dba-8800-bf6a02d50c9c	72	496f13229a1e4bd8aa77d709985498970b5f543c8605a5250048d1a1e579fa90	2026-11-01 23:19:00.077534+01	2026-11-01 22:46:41.00113+01	2026-11-01 22:49:00.077534+01	2026-11-02 06:46:41.00113+01
6bfcd454-506e-479f-a85a-46410e6e2b64	73	4a0b0e66a7a6f62688d2f451d782c9976a3a8dc20195312a5187971971c92c72	2026-11-01 21:03:39.687436+01	2026-11-01 20:32:15.569712+01	2026-11-01 20:33:39.687436+01	2026-11-02 04:32:15.569712+01
c8418dcb-daaa-4a0d-83fd-27bd5c9cec0b	73	f18649e3f829b573ed5914aa1dbbb13cd6cd7076790157652f22f395f40fab3d	2026-11-01 21:37:06.52493+01	2026-11-01 21:07:02.236377+01	2026-11-01 21:07:06.52493+01	2026-11-02 05:07:02.236377+01
7751eb08-7e2a-47c3-b350-327861af4f2d	72	021af30c0922862e6692225efebca771c18f8a47ecc3296fe8cf86a86572adee	2026-11-01 21:40:23.047757+01	2026-11-01 21:08:00.427168+01	2026-11-01 21:10:23.047757+01	2026-11-02 05:08:00.427168+01
1a8813fe-20c7-4834-a777-a205646149e2	73	4456cc4d8f4c5ab7cb447e09af81a8e8a1e6e29366bfcdcf05cd21d34a835a26	2026-11-01 21:20:27.120688+01	2026-11-01 20:41:51.257721+01	2026-11-01 20:50:27.120688+01	2026-11-02 04:41:51.257721+01
d453f874-2e1b-414c-b81e-50e725ccd13d	1	6fadfe29382493f97940eff251538831c420f32267f809736da0e85bd778e994	2026-11-02 17:23:00.306331+01	2026-11-02 16:52:54.229863+01	2026-11-02 16:53:00.306331+01	2026-11-03 00:52:54.229863+01
df05bbf1-9651-476d-8213-337105ff1edf	76	7bb14e569b58a4ba6f5e973707d19be5fd18dd35e697c2b7050205e7e07b11a9	2026-11-02 12:49:08.611438+01	2026-11-02 12:16:44.329529+01	2026-11-02 12:19:08.611438+01	2026-11-02 20:16:44.329529+01
7bb222e4-5b92-4d2a-bf53-7e3c068e752c	72	484faf4177d44daded83a53c587685513cc1c18bb4cbbe221aaa6f4a66f27e50	2026-11-02 12:35:04.777478+01	2026-11-02 12:02:00.290325+01	2026-11-02 12:05:04.777478+01	2026-11-02 20:02:00.290325+01
17c1a8bd-1483-4aa3-8d64-3e7eb3dccf6e	72	1aabf83a0ea9bbbb2fdea6e44fb66f048be14115251e8ef9559395f6d6eb0a6c	2026-11-01 22:22:35.681618+01	2026-11-01 21:30:42.710396+01	2026-11-01 21:52:35.681618+01	2026-11-02 05:30:42.710396+01
992ef565-5be1-4047-8d71-4a72c2e19875	1	67a1f8633ba6b48ac7b2e0a57be742384cac362ba212af83674ad9288bb39cdb	2026-11-02 12:45:10.428153+01	2026-11-02 12:13:49.045009+01	2026-11-02 12:15:10.428153+01	2026-11-02 20:13:49.045009+01
1868e74f-3a5e-4f35-9de1-73f0b30531fb	1	e3f4f342957996b0759b197a575f794e10d12aa5fac5e256fda16c273a789ff0	2026-11-02 12:26:38.005786+01	2026-11-02 11:44:54.904921+01	2026-11-02 11:56:38.005786+01	2026-11-02 19:44:54.904921+01
6cdf3a99-13ae-4252-989e-e1f0733e6c1a	73	389ca68239e2f203c4b2e80bf74a1b4104fa98b6c5d6460bcd1da48aa7d72192	2026-11-02 17:06:25.799374+01	2026-11-02 16:24:11.851238+01	2026-11-02 16:36:25.799374+01	2026-11-03 00:24:11.851238+01
64e72652-1608-42b3-880c-819fc2ffeb3e	72	2e8356c1fa41b9091eb2e9677cf3de38b5668c38874b0141d27668e03561d306	2026-11-02 17:06:44.739975+01	2026-11-02 16:26:29.61512+01	2026-11-02 16:36:44.739975+01	2026-11-03 00:26:29.61512+01
662d0a2f-5262-4d00-ae1e-e3a586414653	77	9c50ba47369c509c61ff9e28c489d2c5f582eb6a2e6729a7c1551e587c3b70cf	2026-11-02 17:27:19.616526+01	2026-11-02 16:54:44.044277+01	2026-11-02 16:57:19.616526+01	2026-11-03 00:54:44.044277+01
08bd3411-c3b7-4e3c-8dd2-7124029a0a27	72	b5c89553eab223390393cdc2a00d60e0102ef306e260db11e73c35561c88ceb0	2026-11-02 17:48:10.238103+01	2026-11-02 17:15:49.845732+01	2026-11-02 17:18:10.238103+01	2026-11-03 01:15:49.845732+01
9e41e06e-b2cb-425f-a73b-8c45842f6636	1	132c6a30d0058f566af6863a52308b8817aa425bbf5e39013a2d91eaab05cd9d	2026-11-02 18:44:07.832527+01	2026-11-02 18:11:10.168283+01	2026-11-02 18:14:07.832527+01	2026-11-03 02:11:10.168283+01
84a281c4-689b-4542-8558-20728dfd8818	79	2ca0a5843d24e335f978bde095c308fae9f45523a78424fda20390bc769c78f0	2026-11-02 18:46:15.127061+01	2026-11-02 18:16:09.850932+01	2026-11-02 18:16:15.127061+01	2026-11-03 02:16:09.850932+01
\.


--
-- Data for Name: soumissions_code; Type: TABLE DATA; Schema: public; Owner: ihm2026
--

COPY public.soumissions_code (id, tentative_id, langage, source, sortie, erreur, statut, duree_ms, cree_le) FROM stdin;
\.


--
-- Data for Name: tentatives; Type: TABLE DATA; Schema: public; Owner: ihm2026
--

COPY public.tentatives (id, apprenant_id, test_id, numero, statut, note, commencee_le, soumise_le, enseignant_id, sujet_snapshot, reponses_snapshot, code_source, note_brouillon, commentaire_brouillon, decision_brouillon, publie_le, seuil_snapshot) FROM stdin;
1	72	2	1	CORRIGEE	20.00	2026-11-01 21:16:32.28822+01	2026-11-01 21:19:49.377933+01	73	[{"id": 4, "type": "REPONSE_COURTE", "enonce": "Explique avec tes propres mots ce qu’est une variable en Python et donne un exemple d’affectation.", "propositions": []}, {"id": 5, "type": "REPONSE_COURTE", "enonce": "Indique le type de chacune de ces valeurs : \\"Bonjour\\", 18, 12.5 et False.", "propositions": []}, {"id": 6, "type": "REPONSE_COURTE", "enonce": "Écris quatre affectations : prenom contenant \\"Karim\\", age contenant 19, moyenne contenant 13.5 et est_inscrit contenant True.", "propositions": []}, {"id": 7, "type": "REPONSE_COURTE", "enonce": "Quelle différence existe-t-il entre age = 19 et age = \\"19\\" ? Précise le type de chaque valeur.", "propositions": []}, {"id": 8, "type": "REPONSE_COURTE", "enonce": "On exécute successivement age = 19, puis age = age + 1, puis print(age). Quelle valeur est affichée ? Explique pourquoi.", "propositions": []}, {"id": 9, "type": "REPONSE_COURTE", "enonce": "Le nom de variable 2notes est-il valide en Python ? Justifie et propose un nom valide.", "propositions": []}, {"id": 10, "type": "REPONSE_COURTE", "enonce": "Écris une instruction qui affiche le texte \\"Moyenne :\\" suivi de la valeur de la variable moyenne.", "propositions": []}, {"id": 11, "type": "REPONSE_COURTE", "enonce": "Écris un petit programme qui crée une variable ville contenant une chaîne de caractères et une variable temperature contenant un nombre décimal, puis affiche leurs valeurs et leurs types.", "propositions": []}]	{"4": "age = 19", "5": "\\"Bonjour\\" : str, chaîne de caractères.\\n18 : int, nombre entier.\\n12.5 : float, nombre décimal.\\nFalse : bool, booléen", "6": "prenom = \\"Karim\\"\\nage = 19\\nmoyenne = 13.5\\nest_inscrit = True", "7": "age = 19", "8": "Le programme affiche 20 : age vaut initialement 19, puis age = age + 1 lui affecte la valeur 20.", "9": "notes2 = 15", "10": "print(\\"Moyenne :\\", moyenne)", "11": "ville = \\"Bouira\\"\\ntemperature = 24.5\\n\\nprint(\\"Ville :\\", ville)\\nprint(\\"Type de ville :\\", type(ville))\\n\\nprint(\\"Température :\\", temperature)\\nprint(\\"Type de temperature :\\", type(temperature))"}		20.00	Very nice	t	2026-11-01 21:21:52.665993+01	20
2	72	2	2	CORRIGEE	0.00	2026-11-01 21:22:17.852257+01	2026-11-01 21:49:39.345686+01	73	[{"id": 4, "type": "REPONSE_COURTE", "enonce": "Explique avec tes propres mots ce qu’est une variable en Python et donne un exemple d’affectation.", "propositions": []}, {"id": 5, "type": "REPONSE_COURTE", "enonce": "Indique le type de chacune de ces valeurs : \\"Bonjour\\", 18, 12.5 et False.", "propositions": []}, {"id": 6, "type": "REPONSE_COURTE", "enonce": "Écris quatre affectations : prenom contenant \\"Karim\\", age contenant 19, moyenne contenant 13.5 et est_inscrit contenant True.", "propositions": []}, {"id": 7, "type": "REPONSE_COURTE", "enonce": "Quelle différence existe-t-il entre age = 19 et age = \\"19\\" ? Précise le type de chaque valeur.", "propositions": []}, {"id": 8, "type": "REPONSE_COURTE", "enonce": "On exécute successivement age = 19, puis age = age + 1, puis print(age). Quelle valeur est affichée ? Explique pourquoi.", "propositions": []}, {"id": 9, "type": "REPONSE_COURTE", "enonce": "Le nom de variable 2notes est-il valide en Python ? Justifie et propose un nom valide.", "propositions": []}, {"id": 10, "type": "REPONSE_COURTE", "enonce": "Écris une instruction qui affiche le texte \\"Moyenne :\\" suivi de la valeur de la variable moyenne.", "propositions": []}, {"id": 11, "type": "REPONSE_COURTE", "enonce": "Écris un petit programme qui crée une variable ville contenant une chaîne de caractères et une variable temperature contenant un nombre décimal, puis affiche leurs valeurs et leurs types.", "propositions": []}]	{"4": "dc", "5": "cddsc", "6": "cds", "7": "cdscs", "8": "csdcds", "9": "cds", "10": "cdscsd", "11": "cdscsdsd"}	cdscs	0.00	refaire	f	2026-11-01 21:51:02.810969+01	20
3	72	2	3	CORRIGEE	0.00	2026-11-02 12:02:43.686219+01	2026-11-02 16:27:05.94757+01	73	[{"id": 4, "type": "REPONSE_COURTE", "enonce": "Explique avec tes propres mots ce qu’est une variable en Python et donne un exemple d’affectation.", "propositions": []}, {"id": 5, "type": "REPONSE_COURTE", "enonce": "Indique le type de chacune de ces valeurs : \\"Bonjour\\", 18, 12.5 et False.", "propositions": []}, {"id": 6, "type": "REPONSE_COURTE", "enonce": "Écris quatre affectations : prenom contenant \\"Karim\\", age contenant 19, moyenne contenant 13.5 et est_inscrit contenant True.", "propositions": []}, {"id": 7, "type": "REPONSE_COURTE", "enonce": "Quelle différence existe-t-il entre age = 19 et age = \\"19\\" ? Précise le type de chaque valeur.", "propositions": []}, {"id": 8, "type": "REPONSE_COURTE", "enonce": "On exécute successivement age = 19, puis age = age + 1, puis print(age). Quelle valeur est affichée ? Explique pourquoi.", "propositions": []}, {"id": 9, "type": "REPONSE_COURTE", "enonce": "Le nom de variable 2notes est-il valide en Python ? Justifie et propose un nom valide.", "propositions": []}, {"id": 10, "type": "REPONSE_COURTE", "enonce": "Écris une instruction qui affiche le texte \\"Moyenne :\\" suivi de la valeur de la variable moyenne.", "propositions": []}, {"id": 11, "type": "REPONSE_COURTE", "enonce": "Écris un petit programme qui crée une variable ville contenant une chaîne de caractères et une variable temperature contenant un nombre décimal, puis affiche leurs valeurs et leurs types.", "propositions": []}]	{"4": "dwc", "5": "cdsc", "6": "cdsc", "7": "cdsc", "8": "cdscsd", "9": "cdscdscdsc", "10": "cdscdcd", "11": "cdsdsc"}	cds	0.00	ded	f	2026-11-02 16:28:03.239043+01	20
\.


--
-- Data for Name: tentatives_authentification; Type: TABLE DATA; Schema: public; Owner: ihm2026
--

COPY public.tentatives_authentification (email_normalise, empreinte_source, debut_fenetre, echecs) FROM stdin;
inactive-696e2a3d-fd8f-4519-b4ec-de0d9c7ed88a@example.org	6d99cbd08fc6c99cdb2d942a4cbb097c6b54496bbbc3ffd6351b145508dd2935	2026-10-28 23:29:07.653109+01	1
inactive-e8d2da3a-d59b-4192-acd3-4c30d696d88d@example.org	6d99cbd08fc6c99cdb2d942a4cbb097c6b54496bbbc3ffd6351b145508dd2935	2026-10-28 22:58:47.43059+01	1
inactive-8df0b72e-6a56-4e5b-8ee2-1d47f94d8796@example.org	6d99cbd08fc6c99cdb2d942a4cbb097c6b54496bbbc3ffd6351b145508dd2935	2026-10-28 23:00:23.276519+01	1
inactive-058368a8-612a-4995-88ab-af04becd296e@example.org	6d99cbd08fc6c99cdb2d942a4cbb097c6b54496bbbc3ffd6351b145508dd2935	2026-10-28 23:53:04.469507+01	1
inactive-459713d5-e50f-4f31-88d7-fd7089026f87@example.org	6d99cbd08fc6c99cdb2d942a4cbb097c6b54496bbbc3ffd6351b145508dd2935	2026-10-28 23:03:03.389778+01	1
inactive-09dce9f9-6ec7-404d-9df0-fda31ce96596@example.org	6d99cbd08fc6c99cdb2d942a4cbb097c6b54496bbbc3ffd6351b145508dd2935	2026-10-28 23:09:25.915989+01	1
inactive-a8c6b54c-ff89-47fd-9342-6314dfe27f58@example.org	6d99cbd08fc6c99cdb2d942a4cbb097c6b54496bbbc3ffd6351b145508dd2935	2026-10-29 00:03:29.495336+01	1
inactive-d7b028a0-5a9e-4971-991d-d8718ac021d8@example.org	6d99cbd08fc6c99cdb2d942a4cbb097c6b54496bbbc3ffd6351b145508dd2935	2026-10-28 23:14:55.006403+01	1
inactive-9f604586-d0a8-4790-bbfd-43649ac7ff46@example.org	6d99cbd08fc6c99cdb2d942a4cbb097c6b54496bbbc3ffd6351b145508dd2935	2026-10-29 00:04:21.039723+01	1
inactive-2492aed8-9860-4337-bf6a-54e3dc884219@example.org	6d99cbd08fc6c99cdb2d942a4cbb097c6b54496bbbc3ffd6351b145508dd2935	2026-10-28 23:27:27.416101+01	1
samoumegharba03@gmail.com`	f3ca9d8eeaf33c97c7aa8bf9f01a1aae308d635ebeb8baec5437ebc73e0c7d8c	2026-11-02 11:41:45.303377+01	3
marzoukamine47@gmail.com	f3ca9d8eeaf33c97c7aa8bf9f01a1aae308d635ebeb8baec5437ebc73e0c7d8c	2026-11-02 11:55:30.945232+01	1
\.


--
-- Data for Name: tests; Type: TABLE DATA; Schema: public; Owner: ihm2026
--

COPY public.tests (id, niveau_id, duree_minutes, ordre_aleatoire) FROM stdin;
1	3	45	f
2	16	45	f
3	17	45	f
4	18	45	f
\.


--
-- Data for Name: utilisateurs; Type: TABLE DATA; Schema: public; Owner: ihm2026
--

COPY public.utilisateurs (id, nom, email, mot_de_passe, role, actif, cree_le, derniere_connexion, statut_inscription) FROM stdin;
2	Sofia Meziane	sofia.meziane@univ.dz	$2a$12$Z83bPHDLDSek/07EXjBjQ.n7DVbUa7BZ7POhY4EI6F9L7ZOhjhUmG	ENSEIGNANT	t	2026-10-28 21:21:12.527846+01	\N	ACCEPTE
3	Amina Bouzid	amina.bouzid@univ.dz	$2a$12$qzlBZw40ly0H9avL/iiegOWMRT2BmSIIdsZZBOo4BPMEb3jQyM83y	APPRENANT	t	2026-10-28 21:21:12.527846+01	\N	ACCEPTE
4	Karim Haddad	karim.haddad@univ.dz	$2a$12$NlPGu21.WznKr4SSRDWFju8Sm21GpwZtPF8Xtq78.jjqxkvkbAC2m	APPRENANT	t	2026-10-28 21:21:12.527846+01	\N	ACCEPTE
5	Omar Belkacem	omar.belkacem@univ.dz	$2a$12$7TJHOKqFIPqBEIDCnJMNvuTsXtkebZ9bSywj9rAD.QjJ3lTYIuEw2	APPRENANT	t	2026-10-28 21:21:12.527846+01	\N	ACCEPTE
6	Lina Saadi	lina.saadi@univ.dz	$2a$12$qYgMoIDh/ujhUvh2JMEZcu/II7NRnibSdTl0473zektyo1D8/v8Gu	APPRENANT	t	2026-10-28 21:21:12.527846+01	\N	ACCEPTE
1	Samou	samoumegharba210@gmail.com	$2a$12$.vwNgXtGijgz0//57.DZRuIIqcEBVBG.yb6y19z0ao0VIV6IeHaEK	ADMINISTRATEUR	t	2026-10-28 21:21:12.527846+01	\N	ACCEPTE
73	Sam	samoumegharba03@gmail.com	$2a$12$dx5YKpdGmxRUA8Bcj1oqSOKt/2Zi6tYi/VIQ.yvBU8TYZLPMdgbdS	ENSEIGNANT	t	2026-11-01 15:35:19.350959+01	\N	ACCEPTE
72	samou	samoumegharba76@gmail.com	$2a$12$mW/6U4k5k6TQOGEB.ixnR.EN/pYCban5s2azRFiahNvb5ViBFobWu	APPRENANT	t	2026-11-01 15:30:18.646482+01	\N	ACCEPTE
7	Yacine Rami	yacine.rami@univ.dz	$2a$12$lg/irF5a2pqKVVOua0oawO9BriJh4CpKEvPpXRuX9izyImOLi1voW	APPRENANT	f	2026-10-28 21:21:12.527846+01	\N	ACCEPTE
75	Marzouk Amine	marzoukamine47@gmail.com	$2a$12$.UahLmYmrRuh4qd1TkcslOeomUwe.1hsLS8cehEpqTxXT7ATur6ti	ENSEIGNANT	f	2026-11-02 11:54:30.338088+01	\N	ACCEPTE
76	Merzouk Amine	merzoukamine47@gmail.com	$2a$12$jpprJ0fsCnS/JPHVrh7Z9eolz1.FYV6LH2k3s4Pmim9tKVIZCaFhy	ENSEIGNANT	t	2026-11-02 11:56:31.748516+01	\N	ACCEPTE
77	Aymen	aymenserdani982@gmail.com	$2a$12$naOaUzznzXKpzpkzdTIqFeZY/KjB6R7vOa0emWMO/NvEfkDb0Rffu	ENSEIGNANT	t	2026-11-02 16:51:09.650699+01	\N	ACCEPTE
79	samou	amnezrouk07@gmail.com	$2a$12$ORDJ2yp5Ou4t64i/VmSOkO9Pj2ui5rjIDKcG3/IJMeXG0uhPMUPou	APPRENANT	t	2026-11-02 18:13:41.055494+01	\N	ACCEPTE
\.


--
-- Name: agent_ia_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ihm2026
--

SELECT pg_catalog.setval('public.agent_ia_id_seq', 1, true);


--
-- Name: aides_ia_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ihm2026
--

SELECT pg_catalog.setval('public.aides_ia_id_seq', 1, false);


--
-- Name: blocs_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ihm2026
--

SELECT pg_catalog.setval('public.blocs_id_seq', 50, true);


--
-- Name: cours_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ihm2026
--

SELECT pg_catalog.setval('public.cours_id_seq', 24, true);


--
-- Name: entrainements_niveaux_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ihm2026
--

SELECT pg_catalog.setval('public.entrainements_niveaux_id_seq', 3, true);


--
-- Name: ia_fragments_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ihm2026
--

SELECT pg_catalog.setval('public.ia_fragments_id_seq', 47, true);


--
-- Name: journal_audit_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ihm2026
--

SELECT pg_catalog.setval('public.journal_audit_id_seq', 52, true);


--
-- Name: langages_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ihm2026
--

SELECT pg_catalog.setval('public.langages_id_seq', 3, true);


--
-- Name: niveaux_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ihm2026
--

SELECT pg_catalog.setval('public.niveaux_id_seq', 18, true);


--
-- Name: parcours_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ihm2026
--

SELECT pg_catalog.setval('public.parcours_id_seq', 5, true);


--
-- Name: progressions_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ihm2026
--

SELECT pg_catalog.setval('public.progressions_id_seq', 8, true);


--
-- Name: propositions_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ihm2026
--

SELECT pg_catalog.setval('public.propositions_id_seq', 12, true);


--
-- Name: questions_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ihm2026
--

SELECT pg_catalog.setval('public.questions_id_seq', 27, true);


--
-- Name: reponses_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ihm2026
--

SELECT pg_catalog.setval('public.reponses_id_seq', 1, false);


--
-- Name: ressources_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ihm2026
--

SELECT pg_catalog.setval('public.ressources_id_seq', 1, false);


--
-- Name: soumissions_code_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ihm2026
--

SELECT pg_catalog.setval('public.soumissions_code_id_seq', 1, false);


--
-- Name: tentatives_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ihm2026
--

SELECT pg_catalog.setval('public.tentatives_id_seq', 3, true);


--
-- Name: tests_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ihm2026
--

SELECT pg_catalog.setval('public.tests_id_seq', 4, true);


--
-- Name: utilisateurs_id_seq; Type: SEQUENCE SET; Schema: public; Owner: ihm2026
--

SELECT pg_catalog.setval('public.utilisateurs_id_seq', 80, true);


--
-- Name: affectations_pedagogiques affectations_pedagogiques_pkey; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.affectations_pedagogiques
    ADD CONSTRAINT affectations_pedagogiques_pkey PRIMARY KEY (apprenant_id);


--
-- Name: agent_ia agent_ia_nom_key; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.agent_ia
    ADD CONSTRAINT agent_ia_nom_key UNIQUE (nom);


--
-- Name: agent_ia agent_ia_pkey; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.agent_ia
    ADD CONSTRAINT agent_ia_pkey PRIMARY KEY (id);


--
-- Name: aides_ia aides_ia_pkey; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.aides_ia
    ADD CONSTRAINT aides_ia_pkey PRIMARY KEY (id);


--
-- Name: blocs blocs_pkey; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.blocs
    ADD CONSTRAINT blocs_pkey PRIMARY KEY (id);


--
-- Name: codes_connexion codes_connexion_pkey; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.codes_connexion
    ADD CONSTRAINT codes_connexion_pkey PRIMARY KEY (identifiant);


--
-- Name: cours cours_pkey; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.cours
    ADD CONSTRAINT cours_pkey PRIMARY KEY (id);


--
-- Name: entrainements_niveaux entrainements_niveaux_pkey; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.entrainements_niveaux
    ADD CONSTRAINT entrainements_niveaux_pkey PRIMARY KEY (id);


--
-- Name: ia_fragments ia_fragments_pkey; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.ia_fragments
    ADD CONSTRAINT ia_fragments_pkey PRIMARY KEY (id);


--
-- Name: invitations_inscription invitations_inscription_pkey; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.invitations_inscription
    ADD CONSTRAINT invitations_inscription_pkey PRIMARY KEY (id);


--
-- Name: journal_audit journal_audit_pkey; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.journal_audit
    ADD CONSTRAINT journal_audit_pkey PRIMARY KEY (id);


--
-- Name: langages langages_code_key; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.langages
    ADD CONSTRAINT langages_code_key UNIQUE (code);


--
-- Name: langages langages_pkey; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.langages
    ADD CONSTRAINT langages_pkey PRIMARY KEY (id);


--
-- Name: niveaux niveaux_pkey; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.niveaux
    ADD CONSTRAINT niveaux_pkey PRIMARY KEY (id);


--
-- Name: parcours_enseignants parcours_enseignants_parcours_id_key; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.parcours_enseignants
    ADD CONSTRAINT parcours_enseignants_parcours_id_key UNIQUE (parcours_id);


--
-- Name: parcours_enseignants parcours_enseignants_pkey; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.parcours_enseignants
    ADD CONSTRAINT parcours_enseignants_pkey PRIMARY KEY (enseignant_id, langage_id);


--
-- Name: parcours parcours_pkey; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.parcours
    ADD CONSTRAINT parcours_pkey PRIMARY KEY (id);


--
-- Name: progressions progressions_pkey; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.progressions
    ADD CONSTRAINT progressions_pkey PRIMARY KEY (id);


--
-- Name: propositions propositions_pkey; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.propositions
    ADD CONSTRAINT propositions_pkey PRIMARY KEY (id);


--
-- Name: questions questions_pkey; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.questions
    ADD CONSTRAINT questions_pkey PRIMARY KEY (id);


--
-- Name: reponses reponses_pkey; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.reponses
    ADD CONSTRAINT reponses_pkey PRIMARY KEY (id);


--
-- Name: ressources ressources_pkey; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.ressources
    ADD CONSTRAINT ressources_pkey PRIMARY KEY (id);


--
-- Name: sessions sessions_jeton_key; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.sessions
    ADD CONSTRAINT sessions_jeton_key UNIQUE (empreinte_jeton);


--
-- Name: sessions sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.sessions
    ADD CONSTRAINT sessions_pkey PRIMARY KEY (id);


--
-- Name: soumissions_code soumissions_code_pkey; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.soumissions_code
    ADD CONSTRAINT soumissions_code_pkey PRIMARY KEY (id);


--
-- Name: tentatives_authentification tentatives_authentification_pkey; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.tentatives_authentification
    ADD CONSTRAINT tentatives_authentification_pkey PRIMARY KEY (email_normalise, empreinte_source);


--
-- Name: tentatives tentatives_pkey; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.tentatives
    ADD CONSTRAINT tentatives_pkey PRIMARY KEY (id);


--
-- Name: tests tests_niveau_id_key; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.tests
    ADD CONSTRAINT tests_niveau_id_key UNIQUE (niveau_id);


--
-- Name: tests tests_pkey; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.tests
    ADD CONSTRAINT tests_pkey PRIMARY KEY (id);


--
-- Name: blocs uq_bloc_position; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.blocs
    ADD CONSTRAINT uq_bloc_position UNIQUE (cours_id, "position");


--
-- Name: niveaux uq_niveau_rang; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.niveaux
    ADD CONSTRAINT uq_niveau_rang UNIQUE (parcours_id, rang);


--
-- Name: progressions uq_progression; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.progressions
    ADD CONSTRAINT uq_progression UNIQUE (apprenant_id, niveau_id);


--
-- Name: propositions uq_proposition_position; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.propositions
    ADD CONSTRAINT uq_proposition_position UNIQUE (question_id, "position");


--
-- Name: questions uq_question_position; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.questions
    ADD CONSTRAINT uq_question_position UNIQUE (test_id, "position");


--
-- Name: reponses uq_reponse; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.reponses
    ADD CONSTRAINT uq_reponse UNIQUE (tentative_id, question_id);


--
-- Name: tentatives uq_tentative_numero; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.tentatives
    ADD CONSTRAINT uq_tentative_numero UNIQUE (apprenant_id, test_id, numero);


--
-- Name: utilisateurs utilisateurs_email_key; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.utilisateurs
    ADD CONSTRAINT utilisateurs_email_key UNIQUE (email);


--
-- Name: utilisateurs utilisateurs_pkey; Type: CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.utilisateurs
    ADD CONSTRAINT utilisateurs_pkey PRIMARY KEY (id);


--
-- Name: ix_audit_date; Type: INDEX; Schema: public; Owner: ihm2026
--

CREATE INDEX ix_audit_date ON public.journal_audit USING btree (cree_le DESC);


--
-- Name: ix_codes_connexion_expiration; Type: INDEX; Schema: public; Owner: ihm2026
--

CREATE INDEX ix_codes_connexion_expiration ON public.codes_connexion USING btree (expire_le);


--
-- Name: ix_copies_enseignant; Type: INDEX; Schema: public; Owner: ihm2026
--

CREATE INDEX ix_copies_enseignant ON public.tentatives USING btree (enseignant_id, statut);


--
-- Name: ix_entrainements_niveau; Type: INDEX; Schema: public; Owner: ihm2026
--

CREATE INDEX ix_entrainements_niveau ON public.entrainements_niveaux USING btree (niveau_id, publie);


--
-- Name: ix_fragments_langage; Type: INDEX; Schema: public; Owner: ihm2026
--

CREATE INDEX ix_fragments_langage ON public.ia_fragments USING btree (langage);


--
-- Name: ix_fragments_recherche; Type: INDEX; Schema: public; Owner: ihm2026
--

CREATE INDEX ix_fragments_recherche ON public.ia_fragments USING gin (to_tsvector('french'::regconfig, contenu));


--
-- Name: ix_fragments_vecteur; Type: INDEX; Schema: public; Owner: ihm2026
--

CREATE INDEX ix_fragments_vecteur ON public.ia_fragments USING hnsw (vecteur public.vector_cosine_ops);


--
-- Name: ix_invitations_inscription_expiration; Type: INDEX; Schema: public; Owner: ihm2026
--

CREATE INDEX ix_invitations_inscription_expiration ON public.invitations_inscription USING btree (expire_le);


--
-- Name: ix_utilisateurs_admin_domaine; Type: INDEX; Schema: public; Owner: ihm2026
--

CREATE INDEX ix_utilisateurs_admin_domaine ON public.utilisateurs USING btree (lower(split_part(email, '@'::text, 2)), statut_inscription, cree_le, id);


--
-- Name: ix_utilisateurs_admin_role_statut_date; Type: INDEX; Schema: public; Owner: ihm2026
--

CREATE INDEX ix_utilisateurs_admin_role_statut_date ON public.utilisateurs USING btree (role, statut_inscription, cree_le, id);


--
-- Name: ix_utilisateurs_admin_statut_date; Type: INDEX; Schema: public; Owner: ihm2026
--

CREATE INDEX ix_utilisateurs_admin_statut_date ON public.utilisateurs USING btree (statut_inscription, cree_le, id);


--
-- Name: uq_copie_ouverte; Type: INDEX; Schema: public; Owner: ihm2026
--

CREATE UNIQUE INDEX uq_copie_ouverte ON public.tentatives USING btree (apprenant_id, test_id) WHERE ((enseignant_id IS NOT NULL) AND (statut = ANY (ARRAY['EN_COURS'::public.statut_tentative, 'SOUMISE'::public.statut_tentative])));


--
-- Name: uq_une_bonne_reponse; Type: INDEX; Schema: public; Owner: ihm2026
--

CREATE UNIQUE INDEX uq_une_bonne_reponse ON public.propositions USING btree (question_id) WHERE correcte;


--
-- Name: uq_utilisateurs_email_insensible; Type: INDEX; Schema: public; Owner: ihm2026
--

CREATE UNIQUE INDEX uq_utilisateurs_email_insensible ON public.utilisateurs USING btree (lower(email));


--
-- Name: ux_invitations_inscription_actives; Type: INDEX; Schema: public; Owner: ihm2026
--

CREATE UNIQUE INDEX ux_invitations_inscription_actives ON public.invitations_inscription USING btree (lower(email_cible), role_cible) WHERE (utilise_le IS NULL);


--
-- Name: progressions trg_proteger_validation; Type: TRIGGER; Schema: public; Owner: ihm2026
--

CREATE TRIGGER trg_proteger_validation BEFORE UPDATE ON public.progressions FOR EACH ROW EXECUTE FUNCTION public.proteger_validation();


--
-- Name: affectations_pedagogiques affectations_pedagogiques_affecte_par_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.affectations_pedagogiques
    ADD CONSTRAINT affectations_pedagogiques_affecte_par_fkey FOREIGN KEY (affecte_par) REFERENCES public.utilisateurs(id);


--
-- Name: affectations_pedagogiques affectations_pedagogiques_apprenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.affectations_pedagogiques
    ADD CONSTRAINT affectations_pedagogiques_apprenant_id_fkey FOREIGN KEY (apprenant_id) REFERENCES public.utilisateurs(id);


--
-- Name: affectations_pedagogiques affectations_pedagogiques_enseignant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.affectations_pedagogiques
    ADD CONSTRAINT affectations_pedagogiques_enseignant_id_fkey FOREIGN KEY (enseignant_id) REFERENCES public.utilisateurs(id);


--
-- Name: aides_ia aides_ia_agent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.aides_ia
    ADD CONSTRAINT aides_ia_agent_id_fkey FOREIGN KEY (agent_id) REFERENCES public.agent_ia(id) ON DELETE SET NULL;


--
-- Name: aides_ia aides_ia_apprenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.aides_ia
    ADD CONSTRAINT aides_ia_apprenant_id_fkey FOREIGN KEY (apprenant_id) REFERENCES public.utilisateurs(id) ON DELETE CASCADE;


--
-- Name: aides_ia aides_ia_tentative_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.aides_ia
    ADD CONSTRAINT aides_ia_tentative_id_fkey FOREIGN KEY (tentative_id) REFERENCES public.tentatives(id) ON DELETE SET NULL;


--
-- Name: blocs blocs_cours_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.blocs
    ADD CONSTRAINT blocs_cours_id_fkey FOREIGN KEY (cours_id) REFERENCES public.cours(id) ON DELETE CASCADE;


--
-- Name: codes_connexion codes_connexion_utilisateur_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.codes_connexion
    ADD CONSTRAINT codes_connexion_utilisateur_id_fkey FOREIGN KEY (utilisateur_id) REFERENCES public.utilisateurs(id) ON DELETE CASCADE;


--
-- Name: cours cours_niveau_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.cours
    ADD CONSTRAINT cours_niveau_id_fkey FOREIGN KEY (niveau_id) REFERENCES public.niveaux(id) ON DELETE CASCADE;


--
-- Name: entrainements_niveaux entrainements_niveaux_cree_par_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.entrainements_niveaux
    ADD CONSTRAINT entrainements_niveaux_cree_par_fkey FOREIGN KEY (cree_par) REFERENCES public.utilisateurs(id);


--
-- Name: entrainements_niveaux entrainements_niveaux_niveau_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.entrainements_niveaux
    ADD CONSTRAINT entrainements_niveaux_niveau_id_fkey FOREIGN KEY (niveau_id) REFERENCES public.niveaux(id) ON DELETE CASCADE;


--
-- Name: ia_fragments ia_fragments_cours_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.ia_fragments
    ADD CONSTRAINT ia_fragments_cours_id_fkey FOREIGN KEY (cours_id) REFERENCES public.cours(id) ON DELETE CASCADE;


--
-- Name: ia_fragments ia_fragments_niveau_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.ia_fragments
    ADD CONSTRAINT ia_fragments_niveau_id_fkey FOREIGN KEY (niveau_id) REFERENCES public.niveaux(id) ON DELETE CASCADE;


--
-- Name: invitations_inscription invitations_inscription_cree_par_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.invitations_inscription
    ADD CONSTRAINT invitations_inscription_cree_par_fkey FOREIGN KEY (cree_par) REFERENCES public.utilisateurs(id) ON DELETE SET NULL;


--
-- Name: journal_audit journal_audit_acteur_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.journal_audit
    ADD CONSTRAINT journal_audit_acteur_id_fkey FOREIGN KEY (acteur_id) REFERENCES public.utilisateurs(id) ON DELETE SET NULL;


--
-- Name: niveaux niveaux_cree_par_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.niveaux
    ADD CONSTRAINT niveaux_cree_par_fkey FOREIGN KEY (cree_par) REFERENCES public.utilisateurs(id) ON DELETE SET NULL;


--
-- Name: niveaux niveaux_parcours_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.niveaux
    ADD CONSTRAINT niveaux_parcours_id_fkey FOREIGN KEY (parcours_id) REFERENCES public.parcours(id) ON DELETE CASCADE;


--
-- Name: parcours_enseignants parcours_enseignants_enseignant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.parcours_enseignants
    ADD CONSTRAINT parcours_enseignants_enseignant_id_fkey FOREIGN KEY (enseignant_id) REFERENCES public.utilisateurs(id) ON DELETE RESTRICT;


--
-- Name: parcours_enseignants parcours_enseignants_langage_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.parcours_enseignants
    ADD CONSTRAINT parcours_enseignants_langage_id_fkey FOREIGN KEY (langage_id) REFERENCES public.langages(id) ON DELETE RESTRICT;


--
-- Name: parcours_enseignants parcours_enseignants_parcours_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.parcours_enseignants
    ADD CONSTRAINT parcours_enseignants_parcours_id_fkey FOREIGN KEY (parcours_id) REFERENCES public.parcours(id) ON DELETE CASCADE;


--
-- Name: parcours parcours_langage_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.parcours
    ADD CONSTRAINT parcours_langage_id_fkey FOREIGN KEY (langage_id) REFERENCES public.langages(id) ON DELETE RESTRICT;


--
-- Name: progressions progressions_apprenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.progressions
    ADD CONSTRAINT progressions_apprenant_id_fkey FOREIGN KEY (apprenant_id) REFERENCES public.utilisateurs(id) ON DELETE CASCADE;


--
-- Name: progressions progressions_niveau_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.progressions
    ADD CONSTRAINT progressions_niveau_id_fkey FOREIGN KEY (niveau_id) REFERENCES public.niveaux(id) ON DELETE CASCADE;


--
-- Name: propositions propositions_question_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.propositions
    ADD CONSTRAINT propositions_question_id_fkey FOREIGN KEY (question_id) REFERENCES public.questions(id) ON DELETE CASCADE;


--
-- Name: questions questions_test_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.questions
    ADD CONSTRAINT questions_test_id_fkey FOREIGN KEY (test_id) REFERENCES public.tests(id) ON DELETE CASCADE;


--
-- Name: reponses reponses_proposition_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.reponses
    ADD CONSTRAINT reponses_proposition_id_fkey FOREIGN KEY (proposition_id) REFERENCES public.propositions(id) ON DELETE SET NULL;


--
-- Name: reponses reponses_question_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.reponses
    ADD CONSTRAINT reponses_question_id_fkey FOREIGN KEY (question_id) REFERENCES public.questions(id) ON DELETE CASCADE;


--
-- Name: reponses reponses_tentative_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.reponses
    ADD CONSTRAINT reponses_tentative_id_fkey FOREIGN KEY (tentative_id) REFERENCES public.tentatives(id) ON DELETE CASCADE;


--
-- Name: ressources ressources_niveau_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.ressources
    ADD CONSTRAINT ressources_niveau_id_fkey FOREIGN KEY (niveau_id) REFERENCES public.niveaux(id) ON DELETE CASCADE;


--
-- Name: sessions sessions_utilisateur_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.sessions
    ADD CONSTRAINT sessions_utilisateur_id_fkey FOREIGN KEY (utilisateur_id) REFERENCES public.utilisateurs(id) ON DELETE CASCADE;


--
-- Name: soumissions_code soumissions_code_tentative_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.soumissions_code
    ADD CONSTRAINT soumissions_code_tentative_id_fkey FOREIGN KEY (tentative_id) REFERENCES public.tentatives(id) ON DELETE CASCADE;


--
-- Name: tentatives tentatives_apprenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.tentatives
    ADD CONSTRAINT tentatives_apprenant_id_fkey FOREIGN KEY (apprenant_id) REFERENCES public.utilisateurs(id) ON DELETE CASCADE;


--
-- Name: tentatives tentatives_enseignant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.tentatives
    ADD CONSTRAINT tentatives_enseignant_id_fkey FOREIGN KEY (enseignant_id) REFERENCES public.utilisateurs(id);


--
-- Name: tentatives tentatives_test_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.tentatives
    ADD CONSTRAINT tentatives_test_id_fkey FOREIGN KEY (test_id) REFERENCES public.tests(id) ON DELETE CASCADE;


--
-- Name: tests tests_niveau_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: ihm2026
--

ALTER TABLE ONLY public.tests
    ADD CONSTRAINT tests_niveau_id_fkey FOREIGN KEY (niveau_id) REFERENCES public.niveaux(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

\unrestrict d3WtFVvXeusMLEIVqywmkA1stv0V028T51CUAxQ0Je5zXvhIBTYhg2NE0iYZG2K

