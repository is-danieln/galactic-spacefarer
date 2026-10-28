using { SpacefarerService as service } from '../srv/spacefarer-service';

annotate service.Spacefarers with @(
  UI.HeaderInfo: {
    TypeName: 'Spacefarer', TypeNamePlural: 'Spacefarers',
    Title: { $Type: 'UI.DataField', Value: fullName },
    Description: { $Type: 'UI.DataField', Value: navigationRank }
  },
  UI.SelectionFields: [fullName, spacesuitColor, department_code, navigationRank],
  UI.LineItem: [
    { Value: fullName },
    { Value: stardustCollection },
    { Value: wormholeNavigationSkill },
    { Value: navigationRank },
    { Value: spacesuitColor },
    { Value: originPlanet.name },
    { Value: department.name }
  ],
  UI.Facets: [
    { $Type: 'UI.ReferenceFacet', ID: 'Profile', Label: 'Profile', Target: '@UI.FieldGroup#Profile' },
    { $Type: 'UI.ReferenceFacet', ID: 'Skills', Label: 'Cosmic skills', Target: '@UI.FieldGroup#Skills' },
    { $Type: 'UI.ReferenceFacet', ID: 'Assignment', Label: 'Assignment', Target: '@UI.FieldGroup#Assignment' }
  ],
  UI.FieldGroup#Profile: { Data: [{ Value: fullName }, { Value: email }, { Value: spacesuitColor }] },
  UI.FieldGroup#Skills: { Data: [{ Value: stardustCollection }, { Value: wormholeNavigationSkill }, { Value: navigationRank }] },
  UI.FieldGroup#Assignment: { Data: [{ Value: originPlanet_code }, { Value: department_code }, { Value: position_code }] }
);

annotate service.Spacefarers with {
  ID @UI.Hidden;
  fullName @title: 'Name';
  email @title: 'Email address';
  stardustCollection @title: 'Stardust collected';
  wormholeNavigationSkill @title: 'Navigation skill (0–100)';
  navigationRank @title: 'Navigation rank';
  spacesuitColor @title: 'Spacesuit color';
  originPlanet @title: 'Origin planet' @Common.Text: originPlanet.name @Common.TextArrangement: #TextOnly
    @Common.ValueListWithFixedValues
    @Common.ValueList: { CollectionPath: 'Planets', Parameters: [
      { $Type: 'Common.ValueListParameterInOut', LocalDataProperty: originPlanet_code, ValueListProperty: 'code' },
      { $Type: 'Common.ValueListParameterDisplayOnly', ValueListProperty: 'name' }
    ] };
  department @title: 'Department' @Common.Text: department.name @Common.TextArrangement: #TextOnly
    @Common.ValueListWithFixedValues
    @Common.ValueList: { CollectionPath: 'Departments', Parameters: [
      { $Type: 'Common.ValueListParameterInOut', LocalDataProperty: department_code, ValueListProperty: 'code' },
      { $Type: 'Common.ValueListParameterDisplayOnly', ValueListProperty: 'name' }
    ] };
  position @title: 'Position' @Common.Text: position.name @Common.TextArrangement: #TextOnly
    @Common.ValueListWithFixedValues
    @Common.ValueList: { CollectionPath: 'Positions', Parameters: [
      { $Type: 'Common.ValueListParameterInOut', LocalDataProperty: position_code, ValueListProperty: 'code' },
      { $Type: 'Common.ValueListParameterDisplayOnly', ValueListProperty: 'name' }
    ] };
  createdAt @UI.Hidden;
  createdBy @UI.Hidden;
  modifiedAt @UI.Hidden;
  modifiedBy @UI.Hidden;
};
