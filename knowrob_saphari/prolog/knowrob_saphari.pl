/** <module> knowrob_saphari

  Copyright (C) 2015 by Daniel Beßler

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions are met:
      * Redistributions of source code must retain the above copyright
        notice, this list of conditions and the following disclaimer.
      * Redistributions in binary form must reproduce the above copyright
        notice, this list of conditions and the following disclaimer in the
        documentation and/or other materials provided with the distribution.
      * Neither the name of the <organization> nor the
        names of its contributors may be used to endorse or promote products
        derived from this software without specific prior written permission.

  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
  DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
  DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

@author Daniel Beßler
@license BSD
*/


:- module(knowrob_saphari,
    [
      agent_marker/4,
      agent_connection_marker/5,
      
      action_designator/3,
      
      intrusion_link/4,
      intrusion_link/5,
      
      saphari_action_events/2,
      saphari_collision_events/2,
      saphari_move_down_events/1,
      saphari_intrusion_events/1,
      
      saphari_visualize_experiment/1,
      saphari_visualize_agents/1,
      saphari_visualize_human/2,
      saphari_visualize_human/3,
      highlight_intrusions/4,
      
      
      saphari_latest_object_detection/2,
      saphari_latest_object_detection/3,
      saphari_latest_object_detections/2,
      saphari_latest_object_detections/3,
      saphari_perception_designator/4
    ]).
:- use_module(library('semweb/rdf_db')).
:- use_module(library('semweb/rdfs')).
:- use_module(library('owl')).
:- use_module(library('rdfs_computable')).
:- use_module(library('owl_parser')).
:- use_module(library('comp_temporal')).
:- use_module(library('knowrob_mongo')).
:- use_module(library('srdl2')).
:- use_module(library('knowrob_cram')).

:- rdf_db:rdf_register_ns(knowrob, 'http://knowrob.org/kb/knowrob.owl#',  [keep(true)]).
:- rdf_db:rdf_register_ns(saphari, 'http://knowrob.org/kb/saphari.owl#', [keep(true)]).
:- rdf_db:rdf_register_ns(knowrob_cram, 'http://knowrob.org/kb/knowrob_cram.owl#', [keep(true)]).

:- rdf_db:rdf_register_ns(openni_human, 'http://knowrob.org/kb/openni_human1.owl#', [keep(true)]).
:- rdf_db:rdf_register_ns(boxy, 'http://knowrob.org/kb/Boxy.owl#', [keep(true)]).

% define predicates as rdf_meta predicates
% (i.e. rdf namespaces are automatically expanded)
:- rdf_meta saphari_visualize_human(r,r,r),
            saphari_visualize_human(r,r),
            saphari_visualize_agents(r),
            saphari_collision_events(r,r),
            saphari_action_events(r,r),
            saphari_move_down_events(r),
            saphari_intrusion_events(r),
            saphari_visualize_experiment(r),
            highlight_intrusions(r,r,r,r),
            agent_marker(r,r,r,r),
            action_designator(r,r,r),
            intrusion_link(r,r,r,r),
            intrusion_link(r,r,r,r,r),
            agent_connection_marker(r,r,r,r,r).

action_designator(TaskContext, Timepoint, Designator) :-
  % action with taskContext
  owl_individual_of(Action, knowrob:'CRAMAction'),
  once((
    rdf_has(Action, knowrob:'taskContext', literal(type(_,TaskContext))),
    % attached designator
    rdf_has(Action, knowrob:'subAction', WithDesignators),
    owl_individual_of(WithDesignators, knowrob:'WithDesignators'),
    rdf_has(WithDesignators, knowrob:'designator', Desig),
    % finally the equated designator
    rdf_has(Desig, knowrob:'equatedDesignator', Designator),
    rdf_has(Designator, knowrob:'equationTime', Timepoint)
  )).

agent_marker(Link, Prefix, Identifier, MarkerId) :-
  term_to_atom(object_without_children(Link), LinkAtom),
  atom_concat(Identifier, '_', Buf),
  atom_concat(Buf, LinkAtom, MarkerId).

agent_connection_marker(Link0, Link1, Prefix, Identifier, MarkerId) :-
  term_to_atom(cylinder_tf(Link0,Link1), CylinderAtom),
  atom_concat(Identifier, '_', Buf),
  atom_concat(Buf, CylinderAtom, MarkerId).

intrusion_link(Human, HumanPrefix, Timeppoint, HumanLink) :-
  intrusion_link(Human, HumanPrefix, Timeppoint, 1.5, HumanLink).

intrusion_link(Human, HumanPrefix, Timeppoint, Threshold, HumanLink) :-
  sub_component(Human, HumanLink),
  owl_has(HumanLink, srdl2comp:'urdfName', literal(HumanUrdfName)),
  atom_concat(HumanPrefix, HumanUrdfName, HumanTf),
  mng_lookup_position('/shoulder_kinect_rgb_frame', HumanTf, Timeppoint, Position),
  nth0(0, Position, XPosition),
  XPosition < Threshold.
  
highlight_intrusion_danger(MarkerId) :-
  marker(MarkerId, MarkerObject),
  marker_highlight(MarkerObject, [1.0,0.0,0.0,1.0]).

highlight_intrusions(HumanIdentifier, Human, HumanPrefix, Timeppoint) :-
  % Find all links that intersect with the safety area of the robot
  findall(HumanLink,
    intrusion_link(Human, HumanPrefix, Timeppoint, HumanLink),
    IntrusionLinks),
  
  forall(member(HumanLink0, IntrusionLinks), ((
    agent_marker(HumanLink0, HumanPrefix, HumanIdentifier, MarkerId),
    % Highlight link marker
    highlight_intrusion_danger(MarkerId),
    % Highlight connection between marker
    forall(member(HumanLink1, IntrusionLinks), ((
      succeeding_link(HumanLink0, HumanLink1),
      agent_connection_marker(HumanLink0, HumanLink1, HumanPrefix, HumanIdentifier, ConnectionMarkerId),
      highlight_intrusion_danger(ConnectionMarkerId)
    ) ; true))
  ) ; true)).

saphari_visualize_human(HumanPrefix, Timeppoint) :-
  saphari_visualize_human(HumanPrefix, HumanPrefix, Timeppoint).

saphari_visualize_human(HumanIdentifier, HumanPrefix, Timeppoint) :-
  marker(stickman(openni_human:'iai_human_robot1'), _, HumanIdentifier),
  marker_tf_prefix(HumanIdentifier, HumanPrefix),
  marker_update(HumanIdentifier, Timeppoint),
  highlight_intrusions(HumanIdentifier, openni_human:'iai_human_robot1', HumanPrefix, Timeppoint).

saphari_collision_events(Type, Events) :-
  CollisionTypes = ['LIGHT-COLLISION', 'STRONG-COLLISION', 'CONTACT', 'SEVERE-COLLISION'],
  member(Type, CollisionTypes),
  findall(Event, (
    owl_individual_of(Event, knowrob:'CRAMAction'),
    rdf_has(Event, knowrob:'taskContext', literal(type(_,Type)))
  ), Events).

saphari_action_events(Type, Events) :-
  ActionTypes = [
      'REPLACEABLE-FUNCTION-MOVE-DOWN-UNTIL-TOUCH',
      'REPLACEABLE-FUNCTION-MOVE-ARM-AWAY',
      'REPLACEABLE-FUNCTION-SAFELY-PERFORM-ACTION',
      'REPLACEABLE-FUNCTION-MOVE-OVER-OBJECT'
  ],
  member(Type, ActionTypes),
  findall(Event, (
    owl_individual_of(Event, knowrob:'CRAMAction'),
    rdf_has(Event, knowrob:'taskContext', literal(type(_,Type)))
  ), Events).

saphari_move_down_events(Events) :-
  findall(Event, (
    owl_individual_of(Event, knowrob:'CRAMAction'),
    rdf_has(Event, knowrob:'taskContext', literal(type(_,'REPLACEABLE-FUNCTION-MOVE-DOWN-UNTIL-TOUCH')))
  ), Events).

saphari_intrusion_events(Events) :-
  findall(Event, (
    owl_individual_of(Event, knowrob:'CRAMAction'),
    rdf_has(Event, knowrob:'taskContext', literal(type(_,'HUMAN-INTRUSION')))
  ), Events).

%saphari_visualize_agents(Timepoint) :-
%  add_agent_visualization('BOXY', boxy:'boxy_robot1', Timepoint, '', ''),
%  
%  forall(event(saphari:'HumanIntrusion', Intrusion, Timepoint), ((
%    owl_has(Intrusion, knowrob:'designator', D),
%    mng_designator_props(D, 'TF-PREFIX', Prefix),
%    saphari_visualize_human(Prefix, Timepoint)
%  ) ; true)).

human_tf_prefix(UserIdJava, Prefix) :-
  jpl_call(UserIdJava, intValue, [], UserId),
  atom_concat('/human', UserId, PrefixA),
  atom_concat(PrefixA, '/', Prefix).

saphari_visualize_humans(Timepoint) :-
  time_term(Timepoint, Time),
  MinTimepoint is Time - 0.5,
  
  mng_designator_distinct_values('designator.USER-ID', UserIds),
  forall(member(UserIdJava, UserIds), ((
    ((
      mng_query_latest('logged_designators', one(_), '__recorded', Time, [
        ['__recorded', '>=', date(MinTimepoint)],
        ['designator.USER-ID', '=', UserIdJava]])
    )
    -> (
      human_tf_prefix(UserIdJava, Prefix),
      saphari_visualize_human(Prefix, Timepoint)
    ) ; (
      human_tf_prefix(UserIdJava, Prefix),
      marker_remove(Prefix)
    ))
  ) ; true)).

saphari_visualize_agents(Timepoint) :-
  marker_update(agent(boxy:'boxy_robot1'), Timepoint),
  saphari_visualize_humans(Timepoint).

unasserted_perceived_object(StartTime, EndTime, Perception, Obj) :-
  owl_individual_of(Perception, knowrob:'UIMAPerception'),
  rdf_has(Perception, knowrob:'perceptionResult', Obj),
  
  % Only assert once
  rdf_split_url(_, ObjID, Obj),
  atom_concat('http://knowrob.org/kb/cram_log.owl#Object_', ObjID, InstanceUrl),
  not( owl_individual_of(InstanceUrl, knowrob:'SpatialThing-Localized') ),
  
  % Assert objects which were perceived between StartTime and EndTime
  owl_has(Perception, knowrob:'endTime', Time),
  time_later_then(Time, StartTime),
  time_earlier_then(Time, EndTime).

assert_perceived_objects(StartTime, EndTime, Map) :-
  findall([Obj,LocList], (
     unasserted_perceived_object(StartTime, EndTime, _Perception, Obj),
     designator_location(Obj,LocList)
  ), Objects), !,
  forall( member([Obj,LocList], Objects), (
    create_pose(LocList, Loc),
    add_object_as_semantic_instance(Obj, Loc, EndTime, Map, _Instance)
  )).

saphari_visualize_map(Experiment, Timepoint) :-
  experiment_map(Experiment, Map),
  
  % XXX: Running into computable error "Would end up in deadlock"
  % Create_pose causes this maybe also add_object_as_semantic_instance.
  % Howto fix it?
  %assert_perceived_objects(StartTime, Timepoint, Map),
  marker_remove(object(Map)),
  marker_update(object(Map), Timepoint).

saphari_visualize_experiment(Timepoint) :-
  once(experiment(Experiment, Timepoint)),
  marker_highlight_remove(all),
  saphari_visualize_map(Experiment, Timepoint),
  saphari_visualize_agents(Timepoint), !.


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%  FINAL REVIEW

saphari_latest_object_detections(Classes, dt(TimeRange), Detections) :-
  get_timepoint(T1), !,
  time_term(T1, T1_Term),
  T0_Term is T1_Term - TimeRange,
  atom_concat('http://knowrob.org/kb/knowrob.owl#timepoint_', T0_Term, T0),
  saphari_latest_object_detections(Classes, interval(T0,T1), Detections).

saphari_latest_object_detections(Classes, interval(Start,End), ValidDetections) :-
  saphari_latest_object_detections(Classes, Detections),
  findall( ValidDetection, (
    member(Detection, Detections),
    owl_has(Detection, knowrob:'startTime', StartTime),
    (( time_later_then(StartTime,Start),
       time_earlier_then(StartTime,End) )
    -> ValidDetection = Detection
    ;  ValidDetection = none
    )
  ), ValidDetections).

saphari_latest_object_detections(Classes, Detections) :-
  findall(Detection, (
    member(Class, Classes),
    saphari_latest_object_detection(Class, Detection)
  ), Detections).

saphari_perception_designator_class(Obj, ObjJava, Class) :-
  mng_designator_props(Obj, ObjJava, ['RESPONSE'], Class).

saphari_perception_designator_class(Obj, ObjJava, Class) :-
  mng_designator_props(Obj, ObjJava, ['TYPE'], Class).

saphari_perception_designator(Class, Obj, Start, End) :-
  task_type(Perc,knowrob:'UIMAPerception'),
  rdf_has(Perc, knowrob:'perceptionResult', Obj),
  mng_designator(Obj, ObjJava),
  once(saphari_perception_designator_class(Obj, ObjJava, Class)),
  rdf_has(Perc, knowrob:'startTime', Start),
  rdf_has(Perc, knowrob:'endTime', End).

saphari_latest_object_detection(Class, Obj) :-
  get_timepoint(Now), !,
  saphari_latest_object_detection(Class, Obj, Now).

saphari_latest_object_detection(Class, Obj, Now) :-
  saphari_perception_designator(Class, Obj, _, End),
  time_earlier_then(End, Now),
  % Make sure there is no later event
  not((
    saphari_perception_designator(Class, Obj2, _, End2),
    not(Obj = Obj2),
    time_earlier_then(End2, Now),
    time_later_then(End2, End)
  )).
